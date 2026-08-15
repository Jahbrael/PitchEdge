create table odds_extraction_runs (
    id uuid primary key,
    raw_snapshot_id uuid not null references raw_snapshots(id),
    extraction_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    rows_seen integer not null,
    rows_accepted integer not null,
    snapshots_imported integer not null,
    selections_updated integer not null,
    validation_error_count integer not null,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_odds_extraction_counts check (
        rows_seen >= 0
        and rows_accepted >= 0
        and snapshots_imported >= 0
        and selections_updated >= 0
        and validation_error_count >= 0
    ),
    constraint ck_odds_extraction_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_odds_extraction_raw_snapshot_status
    on odds_extraction_runs(raw_snapshot_id, extraction_status, started_at desc);

create index idx_odds_extraction_started_at
    on odds_extraction_runs(started_at desc);

create table pipeline_runs (
    id uuid primary key,
    pipeline_date date not null,
    league_codes varchar(500) not null,
    model_version varchar(80) not null,
    pipeline_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    step_summary_json text,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_pipeline_runs_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_pipeline_runs_date_status
    on pipeline_runs(pipeline_date, pipeline_status);

create index idx_pipeline_runs_started_at
    on pipeline_runs(started_at desc);

create table backtest_runs (
    id uuid primary key,
    league_codes varchar(500) not null,
    model_version varchar(80) not null,
    backtest_date date not null,
    match_date_from date not null,
    match_date_to date not null,
    minimum_sample_size integer not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    total_selections integer not null,
    total_won integer not null,
    total_lost integer not null,
    total_void integer not null,
    total_priced integer not null,
    average_probability numeric(9, 6) not null,
    observed_win_rate numeric(9, 6) not null,
    brier_score numeric(9, 6) not null,
    calibration_error numeric(9, 6) not null,
    average_expected_value numeric(10, 6),
    realized_roi numeric(10, 6),
    status varchar(32) not null,
    summary varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_backtest_runs_dates check (match_date_from <= match_date_to),
    constraint ck_backtest_runs_counts check (
        minimum_sample_size >= 0
        and total_selections >= 0
        and total_won >= 0
        and total_lost >= 0
        and total_void >= 0
        and total_priced >= 0
    )
);

create index idx_backtest_runs_model_date
    on backtest_runs(model_version, backtest_date desc);

create table backtest_market_summaries (
    id uuid primary key,
    backtest_run_id uuid not null references backtest_runs(id) on delete cascade,
    league_id uuid not null references leagues(id),
    market_definition_id uuid not null references market_definitions(id),
    sample_size integer not null,
    won_count integer not null,
    lost_count integer not null,
    void_count integer not null,
    priced_count integer not null,
    observed_win_rate numeric(9, 6) not null,
    average_probability numeric(9, 6) not null,
    brier_score numeric(9, 6) not null,
    calibration_error numeric(9, 6) not null,
    average_expected_value numeric(10, 6),
    realized_roi numeric(10, 6),
    recommended_probability_adjustment numeric(9, 6) not null,
    tuning_recommendation varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_backtest_market_counts check (
        sample_size >= 0
        and won_count >= 0
        and lost_count >= 0
        and void_count >= 0
        and priced_count >= 0
    )
);

create index idx_backtest_market_run
    on backtest_market_summaries(backtest_run_id);

create index idx_backtest_market_league_market
    on backtest_market_summaries(league_id, market_definition_id);
