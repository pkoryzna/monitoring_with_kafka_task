CREATE SCHEMA stats_app;

CREATE TABLE stats_app.recorded_stats (
    machine_name varchar not null,
    stat_name varchar not null,
    machine_timestamp_unix bigint not null,
    stat_value double precision not null,
    insert_timestamp timestamp with time zone not null,
    PRIMARY KEY (machine_name, stat_name, machine_timestamp_unix)
);
