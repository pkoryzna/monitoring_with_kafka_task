# System Monitoring app

## Prerequisites

You'll need the following to build and run this project:

- sbt from https://www.scala-sbt.org/
- Java Development Kit 8+ - depends on your platform; I tested with Ubuntu OpenJDK 11.0.10 
  and Amazon Coretto OpenJDK build 1.8.0_282 on Windows without issues
- Docker - to build and run Consumer part

## Producer app 

Statistics are collected using [OSHI library](https://github.com/oshi/oshi), serialized using Protobuf, 
and sent to Kafka by `StatsProducerApp` in `producer` directory (and the corresponding subproject in sbt build).

The task collecting statistics is scheduled at a fixed interval of 1 second by default, which can be changed.
For notes on particular implementation details, see comments in [ProducerApp sources](producer/src/main/scala/com/koryzna/statproducer/StatsProducerApp.scala).

### Configuration

You will have to supply a configuration file as first argument to the application command line.

Before creating your config file, prepare your `truststore` and `keystore` files by
following Aiven documentation: https://help.aiven.io/en/articles/489572-getting-started-with-aiven-kafka 

Then, create a configuration file for use with Aiven Kafka, modifying the example below:

```
kafka {
    # Properies necessary to connect to your Kafka cluster
    
    bootstrap.servers = "my-kafka-service-9999.aivencloud.com:299999"
    security.protocol = "SSL"
    ssl.endpoint.identification.algorithm = ""
    ssl.truststore.location = "/path/to/client.truststore.jks"
    ssl.truststore.password = "super_secret"
    ssl.keystore.type = "PKCS12"
    ssl.keystore.location = "/path/to/client.keystore.p12"
    ssl.keystore.password = "super_secret"
    ssl.key.password = "super_secret"
}
```

Optionally, you can also override those config values:

|Key|Description|Default value|
|---|-----------|-------------|
|`statproducer.topic`|Topic to send stats records to|`stats_proto`|
|`statproducer.timer.period`|How often to poll for statistics|`1s`|
|`statproducer.termination.timeout`|Timeout for Kafka Producer shutdown|`5s`|

To change these settings, add them to the config file, like this:

```hocon
statproducer.topic = "my_custom_topic"
```

Note: Keys under `kafka` will be supplied directly to Kafka Producer as Java properties and 
must follow the format of [producer configs as described in docs](https://kafka.apache.org/documentation/#producerconfigs).

### Packaging

The project uses SBT Native Packager to produce a ZIP archive with launch scripts and all libraries required
to run (including the app JAR itself).

Run `sbt producer/universal:packageBin` to make the archive - you'll find it in 
`producer/target/universal/producer-0.1.zip` after task is finished. 

### Running

Unzip the archive, `cd` to the created directory and run the bundled launch script:

```
bin/producer -- /path/to/your/producer.conf
```

Note: `--` must be present to forward the following argument to the main method of the application.

For more arguments that can be passed to launch script, see [SBT Native Packager docs](https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/index.html#start-script-options).

For development purposes you can also run the app from SBT shell or your favorite IDE, 
supply the config file path as first argument (without the `--`).


## Consumer

Consumer application polls configured Kafka topic, deserializes the Protobuf messages
and `INSERT`s them into Postgres DB. On succesful insertion, offset will be committed to Kafka and loop starts over.

When consumer app starts, it will perform a DB migration (using [Flyway](https://flywaydb.org/documentation/))
to ensure that schema is up to date. You'll find the migrations in [consumer/src/main/resources/db/migration](consumer/src/main/resources/db/migration).

**Ensure the user you connect with to Postgres has CREATE permissions!** Otherwise, migrations will fail (and the app will crash on startup.)

Consumer app **will skip** any records that fail to deserialize, it will log them on WARN level and move onto the
next one. This is a deliberate decision, made to avoid crashing the app with an invalid value. 

### Database schema

Currently, all events are loaded into a single table defined as such:

```sql
CREATE TABLE stats_app.recorded_stats (
    machine_name varchar not null,
    stat_name varchar not null,
    machine_timestamp_unix bigint not null,
    stat_value double precision not null,
    insert_timestamp timestamp with time zone not null,
    PRIMARY KEY (machine_name, stat_name, machine_timestamp_unix)
);
```

On conflict, the `INSERT` query will ignore duplicate records and NOT overwrite already existing values - 
which could happen, for example, when consumer group ID changes (resetting the offsets.)

### Configuration

Consumer app reads the following environment variables:

|Name|Description|Required|Default value|
|----|-----------|--------|-------------|
|DB_URL|JDBC URL for database to connect to|yes||
|DB_USER|Database user|yes||
|DB_PASSWORD|Database password|yes||
|KAFKA_BOOTSTRAP_SERVERS|Kafka bootstrap servers to connect to|yes||
|KAFKA_SSL_KEYSTORE_PATH|Keystore path (see Producer above)|yes||
|KAFKA_SSL_TRUSTSTORE_PATH|Truststore path (see Producer above)|yes||
|KAFKA_SSL_KEY_PASSWORD|TLS key password (see Producer above)|yes||
|KAFKA_SSL_KEYSTORE_PASSWORD|Keystore password (see Producer above)|yes||
|KAFKA_SSL_TRUSTSTORE_PASSWORD|Truststore password (see Producer above)|yes||
|STATCONSUMER_TOPIC|Kafka Topic to read from. Should match the `statproducer.topic` Producer config|no|`stats_proto`|
|STATCONSUMER_POLLING_PERIOD|How long to wait for new record when polling Kafka|no|`1s`|
|STATCONSUMER_TERMINATION_TIMEOUT|Timeout to close the Kafka Consumer|no|`5s`|
|STATCONSUMER_COMMIT_TIMEOUT|Kafka Consumer commit timeout|no|`5s`|
|KAFKA_CONSUMER_GROUP_ID|Consumer group ID|no|`stats_consumer_default`|

For running on dev machine, I recommend creating a `.env` file.

### Building and packaging

Consumer app is packaged into a Docker image using SBT Native Packager. 

Run `sbt consumer/docker:publishLocal` in the project directory to build the image and publish it to your local Docker registry.

Once the Docker image is built, run it with your environment variables, ensuring that the key files are accessible:

```
docker run --env-file .env --volume /path/to/aiven_keys:/keys:ro pkoryzna/statconsumer:0.1
```

For development purposes you can also run the Consumer from SBT shell or your favorite IDE;
remember to have the appropriate variables set in environment. 

## Common subproject

`common` contains Protobuf definitions (compiled with [ScalaPB](https://scalapb.github.io/docs/)) 
and convenience methods for creating a Kafka Consumer and Producer. 

Both applications' sbt projects include it as dependency. It will automatically get built
on compilation.

## Tests

To run the unit tests, run `sbt test`.

Included integration tests can be run using `scripts/intergration_test`. 
Note that you'll need Docker Compose installed to bring up the test dependencies.

## Misc. notes

### Why read config from env vars in Consumer but not in Producer?

I assumed that Producer would be running as a service on host OS of monitored machine - in this case,
as an administrator you can simply place your config somewhere on the file system (e.g. `/etc`) and
use your favorite init system to launch the app.

On the opposite end, I assumed Consumer will run in a Docker container somewhere in the cloud on Kubernetes,
where a typical way to configure services is via environment variables (with DB passwords sourced from Secrets
or other service like Vault, KMS, etc.)

In case you want to replace the configuration file (say, you want to use plaintext instead of TLS, or customize other 
Consumer properties) you can always override the bundled 
`appplication.conf` with Java properties, like: `-Dconfig.file=/my/custom.conf`.

See Lightbend Config docs for more details https://github.com/lightbend/config#standard-behavior

### What I didn't do, but would in a proper production project?

In general, I would describe current state of the project as _quite decent first sprint_ ;-)

- Producer app collects only two statistics: the number of processes running, and available system memory.
  In a real production system I'd add more stats and provide a way to select the values that user wants sent
  to Kafka;

- look into building (de)serializers for Kafka - I used stock String and ByteArray, 
  (de)serializing Protobuf messages manually; 

- add automatic reading of configs instead of accessing Config fields directly, plus validations - 
  currently there's only a handful of custom keys, the exceptions from Lightbend config get printed out and app exits; 
  for larger project with more keys it'd be worth to validate it and list all the missing ones;

- more refined error handling, with retries where applicable (for instance, in case of DB connection problems);

- build a rpm/deb if required - that really depends on the infrastructure though.

