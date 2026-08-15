create table model_quality_snapshots (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    market_definition_id uuid not null references market_definitions(id),
    model_version varchar(80) not null,
    quality_date date not null,
    sample_size integer not null,
    won_count integer not null,
    lost_count integer not null,
    void_count integer not null,
    observed_win_rate numeric(9, 6) not null,
    average_raw_probability numeric(9, 6) not null,
    brier_score numeric(9, 6) not null,
    calibration_error numeric(9, 6) not null,
    probability_adjustment numeric(9, 6) not null,
    confidence_band varchar(32) not null,
    generated_at timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_model_quality_snapshots unique (league_id, market_definition_id, model_version, quality_date),
    constraint ck_model_quality_counts check (
        sample_size >= 0
        and won_count >= 0
        and lost_count >= 0
        and void_count >= 0
    ),
    constraint ck_model_quality_rates check (
        observed_win_rate >= 0 and observed_win_rate <= 1
        and average_raw_probability >= 0 and average_raw_probability <= 1
        and brier_score >= 0
        and calibration_error >= 0
        and probability_adjustment >= -1 and probability_adjustment <= 1
    )
);

create index idx_model_quality_league_model_date
    on model_quality_snapshots(league_id, model_version, quality_date);

create index idx_model_quality_market_band
    on model_quality_snapshots(market_definition_id, confidence_band);

alter table prediction_selections
    add column raw_probability numeric(7, 6),
    add column confidence_band varchar(32) not null default 'UNRATED',
    add column model_quality_snapshot_id uuid references model_quality_snapshots(id),
    add column calibration_note varchar(500);

update prediction_selections
set raw_probability = probability
where raw_probability is null;

alter table prediction_selections
    alter column raw_probability set not null;

create index idx_prediction_selections_confidence_band
    on prediction_selections(confidence_band);

create index idx_prediction_selections_model_quality
    on prediction_selections(model_quality_snapshot_id);
