create table if not exists event_statistics (
    id uuid primary key,
    match_id uuid not null references matches(id),
    team_id uuid references teams(id),
    raw_snapshot_id uuid references raw_snapshots(id),
    statistic_code varchar(64) not null,
    statistic_name varchar(120) not null,
    numeric_value numeric(12, 4),
    text_value varchar(240),
    period varchar(32),
    source_type varchar(64) not null,
    source_statistic_name varchar(160) not null,
    retrieved_at timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create unique index if not exists ux_event_statistics_identity
    on event_statistics(
        match_id,
        coalesce(team_id, '00000000-0000-0000-0000-000000000000'::uuid),
        statistic_code,
        coalesce(period, 'FULL_TIME'),
        source_type
    );

create index if not exists idx_event_statistics_match_code
    on event_statistics(match_id, statistic_code);

create index if not exists idx_event_statistics_team_code
    on event_statistics(team_id, statistic_code);

create table if not exists league_season_coverage (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    season_label varchar(64) not null,
    has_fixtures boolean not null,
    has_results boolean not null,
    has_team_statistics boolean not null,
    has_event_statistics boolean not null,
    has_lineups boolean not null,
    has_timeline boolean not null,
    has_player_statistics boolean not null,
    has_goals boolean not null,
    has_assists boolean not null,
    has_cards boolean not null,
    has_corners boolean not null,
    has_shots boolean not null,
    has_shots_on_target boolean not null,
    has_passes boolean not null,
    has_saves boolean not null,
    has_xg boolean not null,
    completed_events_checked integer not null,
    events_with_statistics integer not null,
    events_with_lineups integer not null,
    events_with_timeline integer not null,
    players_with_statistics integer not null,
    coverage_percentage numeric(5, 2) not null,
    statistics_coverage_level varchar(32) not null,
    corners_coverage_level varchar(32) not null,
    cards_coverage_level varchar(32) not null,
    last_verified_at timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_league_season_coverage unique (league_id, season_label)
);

create table if not exists league_season_market_availability (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    season_label varchar(64) not null,
    market_code varchar(64) not null,
    available boolean not null,
    coverage_level varchar(32) not null,
    reason varchar(1000),
    last_verified_at timestamp(6) with time zone not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_league_season_market_availability unique (league_id, season_label, market_code)
);

create index if not exists idx_league_season_market_availability_lookup
    on league_season_market_availability(league_id, season_label, market_code, available);
