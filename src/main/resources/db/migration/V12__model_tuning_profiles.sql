create table model_tuning_profiles (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    market_definition_id uuid not null references market_definitions(id),
    source_backtest_run_id uuid references backtest_runs(id),
    model_version varchar(80) not null,
    profile_date date not null,
    sample_size integer not null,
    recommended_probability_adjustment numeric(9, 6) not null,
    applied_probability_adjustment numeric(9, 6) not null,
    tuning_recommendation varchar(32) not null,
    active boolean not null,
    note varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_model_tuning_sample_size check (sample_size >= 0),
    constraint ck_model_tuning_adjustments check (
        recommended_probability_adjustment >= -1
        and recommended_probability_adjustment <= 1
        and applied_probability_adjustment >= -1
        and applied_probability_adjustment <= 1
    ),
    constraint ux_model_tuning_profile unique (league_id, market_definition_id, model_version, profile_date)
);

create index idx_model_tuning_active_lookup
    on model_tuning_profiles(league_id, market_definition_id, model_version, profile_date desc, active);

create index idx_model_tuning_backtest
    on model_tuning_profiles(source_backtest_run_id);

alter table prediction_selections
    add column model_tuning_profile_id uuid references model_tuning_profiles(id),
    add column tuning_adjustment numeric(9, 6),
    add column tuning_note varchar(500);

create index idx_prediction_selections_model_tuning
    on prediction_selections(model_tuning_profile_id);
