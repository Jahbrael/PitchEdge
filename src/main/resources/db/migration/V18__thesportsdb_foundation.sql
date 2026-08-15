alter table raw_snapshots
    add column if not exists endpoint_name varchar(120),
    add column if not exists request_parameters_json text,
    add column if not exists external_entity_id varchar(160),
    add column if not exists external_league_id varchar(160),
    add column if not exists source_season varchar(64),
    add column if not exists external_fixture_id varchar(160),
    add column if not exists external_event_id varchar(160),
    add column if not exists parser_version varchar(80),
    add column if not exists processing_status varchar(40),
    add column if not exists processing_error_summary varchar(1000);

create index if not exists idx_raw_snapshots_endpoint_entity
    on raw_snapshots(endpoint_name, external_entity_id);

create index if not exists idx_raw_snapshots_external_event
    on raw_snapshots(external_event_id);

create table if not exists external_source_mappings (
    id uuid primary key,
    source_type varchar(64) not null,
    entity_type varchar(32) not null,
    internal_entity_id uuid,
    external_entity_id varchar(160) not null,
    league_id uuid references leagues(id),
    season varchar(64),
    status varchar(32) not null,
    external_name varchar(220),
    unresolved_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_external_source_mappings_source_entity_external
        unique (source_type, entity_type, external_entity_id)
);

create index if not exists idx_external_source_mappings_internal
    on external_source_mappings(source_type, entity_type, internal_entity_id);

create index if not exists idx_external_source_mappings_league_status
    on external_source_mappings(league_id, status);
