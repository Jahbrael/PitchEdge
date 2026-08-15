create table team_aliases (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    team_id uuid not null references teams(id),
    alias varchar(160) not null,
    alias_normalized varchar(180) not null,
    source_name varchar(160) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_team_aliases_league_alias unique (league_id, alias_normalized)
);

create index idx_team_aliases_team
    on team_aliases(team_id);

create table extraction_runs (
    id uuid primary key,
    raw_snapshot_id uuid not null references raw_snapshots(id),
    extraction_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    rows_seen integer not null,
    rows_accepted integer not null,
    teams_upserted integer not null,
    matches_upserted integer not null,
    stats_upserted integer not null,
    validation_error_count integer not null,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_extraction_runs_rows_seen check (rows_seen >= 0),
    constraint ck_extraction_runs_rows_accepted check (rows_accepted >= 0),
    constraint ck_extraction_runs_teams_upserted check (teams_upserted >= 0),
    constraint ck_extraction_runs_matches_upserted check (matches_upserted >= 0),
    constraint ck_extraction_runs_stats_upserted check (stats_upserted >= 0),
    constraint ck_extraction_runs_validation_errors check (validation_error_count >= 0),
    constraint ck_extraction_runs_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_extraction_runs_snapshot_status
    on extraction_runs(raw_snapshot_id, extraction_status);

create index idx_extraction_runs_started_at
    on extraction_runs(started_at);

create table extraction_validation_errors (
    id uuid primary key,
    extraction_run_id uuid not null references extraction_runs(id),
    raw_snapshot_id uuid not null references raw_snapshots(id),
    row_number integer,
    field_name varchar(120),
    error_code varchar(80) not null,
    error_message varchar(1000) not null,
    raw_record_json text,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_extraction_validation_errors_row_number check (row_number is null or row_number >= 0)
);

create index idx_extraction_validation_errors_run
    on extraction_validation_errors(extraction_run_id);

create index idx_extraction_validation_errors_snapshot
    on extraction_validation_errors(raw_snapshot_id);

create table match_statistics (
    id uuid primary key,
    match_id uuid not null references matches(id),
    raw_snapshot_id uuid not null references raw_snapshots(id),
    referee varchar(160),
    home_shots integer,
    away_shots integer,
    home_shots_on_target integer,
    away_shots_on_target integer,
    home_fouls integer,
    away_fouls integer,
    home_corners integer,
    away_corners integer,
    home_yellow_cards integer,
    away_yellow_cards integer,
    home_red_cards integer,
    away_red_cards integer,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_match_statistics_match unique (match_id),
    constraint ck_match_statistics_home_shots check (home_shots is null or home_shots >= 0),
    constraint ck_match_statistics_away_shots check (away_shots is null or away_shots >= 0),
    constraint ck_match_statistics_home_sot check (home_shots_on_target is null or home_shots_on_target >= 0),
    constraint ck_match_statistics_away_sot check (away_shots_on_target is null or away_shots_on_target >= 0),
    constraint ck_match_statistics_home_fouls check (home_fouls is null or home_fouls >= 0),
    constraint ck_match_statistics_away_fouls check (away_fouls is null or away_fouls >= 0),
    constraint ck_match_statistics_home_corners check (home_corners is null or home_corners >= 0),
    constraint ck_match_statistics_away_corners check (away_corners is null or away_corners >= 0),
    constraint ck_match_statistics_home_yellow check (home_yellow_cards is null or home_yellow_cards >= 0),
    constraint ck_match_statistics_away_yellow check (away_yellow_cards is null or away_yellow_cards >= 0),
    constraint ck_match_statistics_home_red check (home_red_cards is null or home_red_cards >= 0),
    constraint ck_match_statistics_away_red check (away_red_cards is null or away_red_cards >= 0)
);

create index idx_match_statistics_snapshot
    on match_statistics(raw_snapshot_id);
