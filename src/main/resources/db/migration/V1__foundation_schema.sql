create extension if not exists pgcrypto;

create table leagues (
    id uuid primary key,
    code varchar(64) not null unique,
    name varchar(128) not null,
    country varchar(128) not null,
    tier integer not null,
    active boolean not null,
    scrape_enabled boolean not null,
    current_season varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_leagues_tier_positive check (tier > 0)
);

create index idx_leagues_active_scrape_enabled
    on leagues(active, scrape_enabled);

create table teams (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    canonical_name varchar(160) not null,
    short_name varchar(80) not null,
    country varchar(128) not null,
    external_key varchar(160) not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_teams_league_canonical_name unique (league_id, canonical_name),
    constraint ux_teams_external_key unique (external_key)
);

create index idx_teams_league_active
    on teams(league_id, active);

create table matches (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    home_team_id uuid not null references teams(id),
    away_team_id uuid not null references teams(id),
    match_date date not null,
    kickoff_at timestamp(6) with time zone not null,
    status varchar(32) not null,
    home_score integer,
    away_score integer,
    season_label varchar(32) not null,
    round_label varchar(64),
    venue varchar(160),
    source_fixture_key varchar(180) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_matches_league_teams_kickoff unique (league_id, home_team_id, away_team_id, kickoff_at),
    constraint ck_matches_different_teams check (home_team_id <> away_team_id),
    constraint ck_matches_home_score_non_negative check (home_score is null or home_score >= 0),
    constraint ck_matches_away_score_non_negative check (away_score is null or away_score >= 0)
);

create index idx_matches_league_date_status
    on matches(league_id, match_date, status);

create index idx_matches_home_team_date
    on matches(home_team_id, match_date);

create index idx_matches_away_team_date
    on matches(away_team_id, match_date);

create table market_definitions (
    id uuid primary key,
    code varchar(64) not null unique,
    display_name varchar(128) not null,
    market_type varchar(64) not null,
    selection_value varchar(32) not null,
    threshold numeric(6, 2),
    enabled boolean not null,
    minimum_sample_size integer not null,
    settlement_description varchar(300) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_market_definitions_min_sample_size check (minimum_sample_size >= 0),
    constraint ck_market_definitions_threshold check (threshold is null or threshold >= 0)
);

create index idx_market_definitions_enabled_type
    on market_definitions(enabled, market_type);

create table prediction_selections (
    id uuid primary key,
    match_id uuid not null references matches(id),
    market_definition_id uuid not null references market_definitions(id),
    predicted_value varchar(64) not null,
    probability numeric(7, 6) not null,
    model_version varchar(80) not null,
    generated_at timestamp(6) with time zone not null,
    correlation_group_key varchar(160) not null,
    feature_snapshot_json text,
    outcome varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_prediction_selections_match_market_model unique (match_id, market_definition_id, model_version),
    constraint ck_prediction_selections_probability check (probability >= 0 and probability <= 1)
);

create index idx_prediction_selections_generated_at
    on prediction_selections(generated_at);

create index idx_prediction_selections_probability
    on prediction_selections(probability);

create index idx_prediction_selections_outcome
    on prediction_selections(outcome);

create table data_refresh_logs (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    refresh_date date not null,
    refresh_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    source_count integer not null,
    records_ingested bigint,
    records_rejected bigint,
    raw_payload_reference varchar(500),
    payload_checksum varchar(128),
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_data_refresh_logs_source_count check (source_count >= 0),
    constraint ck_data_refresh_logs_records_ingested check (records_ingested is null or records_ingested >= 0),
    constraint ck_data_refresh_logs_records_rejected check (records_rejected is null or records_rejected >= 0),
    constraint ck_data_refresh_logs_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_data_refresh_logs_league_date_status
    on data_refresh_logs(league_id, refresh_date, refresh_status);

create index idx_data_refresh_logs_started_at
    on data_refresh_logs(started_at);

create unique index ux_data_refresh_logs_one_success_per_league_date
    on data_refresh_logs(league_id, refresh_date)
    where refresh_status = 'SUCCESS';
