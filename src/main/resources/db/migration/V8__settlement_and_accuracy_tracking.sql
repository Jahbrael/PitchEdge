create table settlement_runs (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    model_version varchar(80) not null,
    settlement_date date not null,
    match_date_from date not null,
    match_date_to date not null,
    settlement_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    selections_evaluated integer not null,
    won_count integer not null,
    lost_count integer not null,
    void_count integer not null,
    skipped_count integer not null,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_settlement_runs_date_order check (match_date_from <= match_date_to),
    constraint ck_settlement_runs_counts check (
        selections_evaluated >= 0
        and won_count >= 0
        and lost_count >= 0
        and void_count >= 0
        and skipped_count >= 0
    ),
    constraint ck_settlement_runs_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_settlement_runs_league_model_dates_status
    on settlement_runs(league_id, model_version, settlement_date, match_date_from, match_date_to, settlement_status);

create index idx_settlement_runs_started_at
    on settlement_runs(started_at);

create table model_accuracy_daily (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    market_definition_id uuid not null references market_definitions(id),
    model_version varchar(80) not null,
    accuracy_date date not null,
    settled_selections integer not null,
    won_count integer not null,
    lost_count integer not null,
    void_count integer not null,
    win_rate numeric(9, 6) not null,
    average_probability numeric(9, 6) not null,
    brier_score numeric(9, 6) not null,
    calibration_error numeric(9, 6) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_model_accuracy_daily unique (league_id, market_definition_id, model_version, accuracy_date),
    constraint ck_model_accuracy_daily_counts check (
        settled_selections >= 0
        and won_count >= 0
        and lost_count >= 0
        and void_count >= 0
    ),
    constraint ck_model_accuracy_daily_rates check (
        win_rate >= 0 and win_rate <= 1
        and average_probability >= 0 and average_probability <= 1
        and brier_score >= 0
        and calibration_error >= 0
    )
);

create index idx_model_accuracy_daily_league_model_date
    on model_accuracy_daily(league_id, model_version, accuracy_date);

create index idx_model_accuracy_daily_market_date
    on model_accuracy_daily(market_definition_id, accuracy_date);
