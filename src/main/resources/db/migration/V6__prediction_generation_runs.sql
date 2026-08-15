create table prediction_generation_runs (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    model_version varchar(80) not null,
    calculation_date date not null,
    fixture_date_from date not null,
    fixture_date_to date not null,
    match_statuses varchar(160) not null,
    generation_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    matches_evaluated integer not null,
    selections_generated integer not null,
    selections_skipped integer not null,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_prediction_runs_date_order check (fixture_date_from <= fixture_date_to),
    constraint ck_prediction_runs_matches_evaluated check (matches_evaluated >= 0),
    constraint ck_prediction_runs_generated check (selections_generated >= 0),
    constraint ck_prediction_runs_skipped check (selections_skipped >= 0),
    constraint ck_prediction_runs_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_prediction_runs_league_model_dates_status
    on prediction_generation_runs(league_id, model_version, calculation_date, fixture_date_from, fixture_date_to, match_statuses, generation_status);

create index idx_prediction_runs_started_at
    on prediction_generation_runs(started_at);
