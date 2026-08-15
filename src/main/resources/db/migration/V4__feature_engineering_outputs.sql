create table feature_generation_runs (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    calculation_date date not null,
    season_label varchar(32) not null,
    feature_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    matches_sampled integer not null,
    team_features_generated integer not null,
    league_baselines_generated integer not null,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_feature_runs_matches_sampled check (matches_sampled >= 0),
    constraint ck_feature_runs_team_features check (team_features_generated >= 0),
    constraint ck_feature_runs_league_baselines check (league_baselines_generated >= 0),
    constraint ck_feature_runs_duration check (duration_ms is null or duration_ms >= 0)
);

create index idx_feature_runs_league_date_status
    on feature_generation_runs(league_id, calculation_date, feature_status);

create index idx_feature_runs_started_at
    on feature_generation_runs(started_at);

create table league_baselines (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    season_label varchar(32) not null,
    calculation_date date not null,
    matches_sampled integer not null,
    avg_home_goals numeric(8,4) not null,
    avg_away_goals numeric(8,4) not null,
    avg_total_goals numeric(8,4) not null,
    home_win_rate numeric(7,6) not null,
    draw_rate numeric(7,6) not null,
    away_win_rate numeric(7,6) not null,
    btts_rate numeric(7,6) not null,
    over_1_5_rate numeric(7,6) not null,
    over_2_5_rate numeric(7,6) not null,
    under_3_5_rate numeric(7,6) not null,
    avg_total_corners numeric(8,4),
    avg_total_yellow_cards numeric(8,4),
    red_card_rate numeric(7,6),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_league_baselines_league_season_date unique (league_id, season_label, calculation_date),
    constraint ck_league_baselines_matches check (matches_sampled >= 0),
    constraint ck_league_baselines_home_win_rate check (home_win_rate >= 0 and home_win_rate <= 1),
    constraint ck_league_baselines_draw_rate check (draw_rate >= 0 and draw_rate <= 1),
    constraint ck_league_baselines_away_win_rate check (away_win_rate >= 0 and away_win_rate <= 1),
    constraint ck_league_baselines_btts_rate check (btts_rate >= 0 and btts_rate <= 1),
    constraint ck_league_baselines_over15_rate check (over_1_5_rate >= 0 and over_1_5_rate <= 1),
    constraint ck_league_baselines_over25_rate check (over_2_5_rate >= 0 and over_2_5_rate <= 1),
    constraint ck_league_baselines_under35_rate check (under_3_5_rate >= 0 and under_3_5_rate <= 1),
    constraint ck_league_baselines_red_card_rate check (red_card_rate is null or (red_card_rate >= 0 and red_card_rate <= 1))
);

create index idx_league_baselines_league_date
    on league_baselines(league_id, calculation_date);

create table team_feature_snapshots (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    team_id uuid not null references teams(id),
    season_label varchar(32) not null,
    calculation_date date not null,
    matches_played integer not null,
    home_matches integer not null,
    away_matches integer not null,
    last_5_matches integer not null,
    last_10_matches integer not null,
    points_per_match numeric(8,4) not null,
    last_5_points_per_match numeric(8,4) not null,
    last_10_points_per_match numeric(8,4) not null,
    goals_for_per_match numeric(8,4) not null,
    goals_against_per_match numeric(8,4) not null,
    home_goals_for_per_match numeric(8,4),
    home_goals_against_per_match numeric(8,4),
    away_goals_for_per_match numeric(8,4),
    away_goals_against_per_match numeric(8,4),
    clean_sheet_rate numeric(7,6) not null,
    failed_to_score_rate numeric(7,6) not null,
    btts_rate numeric(7,6) not null,
    over_1_5_rate numeric(7,6) not null,
    over_2_5_rate numeric(7,6) not null,
    under_3_5_rate numeric(7,6) not null,
    corners_for_per_match numeric(8,4),
    corners_against_per_match numeric(8,4),
    yellow_cards_for_per_match numeric(8,4),
    yellow_cards_against_per_match numeric(8,4),
    red_card_rate numeric(7,6),
    form_score numeric(8,4) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_team_features_team_season_date unique (league_id, team_id, season_label, calculation_date),
    constraint ck_team_features_matches_played check (matches_played >= 0),
    constraint ck_team_features_home_matches check (home_matches >= 0),
    constraint ck_team_features_away_matches check (away_matches >= 0),
    constraint ck_team_features_last5 check (last_5_matches >= 0 and last_5_matches <= 5),
    constraint ck_team_features_last10 check (last_10_matches >= 0 and last_10_matches <= 10),
    constraint ck_team_features_clean_sheet_rate check (clean_sheet_rate >= 0 and clean_sheet_rate <= 1),
    constraint ck_team_features_failed_to_score_rate check (failed_to_score_rate >= 0 and failed_to_score_rate <= 1),
    constraint ck_team_features_btts_rate check (btts_rate >= 0 and btts_rate <= 1),
    constraint ck_team_features_over15_rate check (over_1_5_rate >= 0 and over_1_5_rate <= 1),
    constraint ck_team_features_over25_rate check (over_2_5_rate >= 0 and over_2_5_rate <= 1),
    constraint ck_team_features_under35_rate check (under_3_5_rate >= 0 and under_3_5_rate <= 1),
    constraint ck_team_features_red_card_rate check (red_card_rate is null or (red_card_rate >= 0 and red_card_rate <= 1))
);

create index idx_team_features_league_date
    on team_feature_snapshots(league_id, calculation_date);

create index idx_team_features_team_date
    on team_feature_snapshots(team_id, calculation_date);
