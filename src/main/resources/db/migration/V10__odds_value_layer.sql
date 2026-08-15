create table bookmakers (
    id uuid primary key,
    code varchar(64) not null unique,
    display_name varchar(128) not null,
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create index idx_bookmakers_active
    on bookmakers(active);

create table odds_snapshots (
    id uuid primary key,
    match_id uuid not null references matches(id),
    market_definition_id uuid not null references market_definitions(id),
    bookmaker_id uuid not null references bookmakers(id),
    decimal_odds numeric(10, 4) not null,
    implied_probability numeric(7, 6) not null,
    captured_at timestamp(6) with time zone not null,
    source_name varchar(160) not null,
    source_url varchar(500),
    raw_payload_reference varchar(500),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_odds_snapshots_decimal_odds check (decimal_odds > 1),
    constraint ck_odds_snapshots_implied_probability check (implied_probability > 0 and implied_probability <= 1)
);

create index idx_odds_snapshots_match_market_captured
    on odds_snapshots(match_id, market_definition_id, captured_at desc);

create index idx_odds_snapshots_bookmaker_captured
    on odds_snapshots(bookmaker_id, captured_at desc);

create index idx_odds_snapshots_source_name
    on odds_snapshots(source_name);

alter table prediction_selections
    add column best_decimal_odds numeric(10, 4),
    add column best_implied_probability numeric(7, 6),
    add column value_edge numeric(8, 6),
    add column expected_value numeric(10, 6),
    add column value_rating varchar(32) not null default 'NO_ODDS',
    add column best_odds_bookmaker_id uuid references bookmakers(id),
    add column best_odds_snapshot_id uuid references odds_snapshots(id),
    add column odds_captured_at timestamp(6) with time zone,
    add column value_assessed_at timestamp(6) with time zone,
    add column value_note varchar(500);

alter table prediction_selections
    add constraint ck_prediction_selections_best_decimal_odds check (best_decimal_odds is null or best_decimal_odds > 1),
    add constraint ck_prediction_selections_best_implied_probability check (
        best_implied_probability is null
        or (best_implied_probability > 0 and best_implied_probability <= 1)
    ),
    add constraint ck_prediction_selections_value_edge check (value_edge is null or (value_edge >= -1 and value_edge <= 1));

create index idx_prediction_selections_value_rating
    on prediction_selections(value_rating);

create index idx_prediction_selections_expected_value
    on prediction_selections(expected_value);

create index idx_prediction_selections_best_odds_snapshot
    on prediction_selections(best_odds_snapshot_id);
