alter table league_baselines
    add column if not exists requested_season_count integer,
    add column if not exists actual_season_count_used integer,
    add column if not exists season_selection_mode varchar(40),
    add column if not exists selected_season_ids text,
    add column if not exists selected_season_names text,
    add column if not exists current_season_included boolean,
    add column if not exists fallback_applied boolean,
    add column if not exists oldest_data_date date,
    add column if not exists newest_data_date date,
    add column if not exists completed_matches_used integer,
    add column if not exists market_specific_usable_season_count integer,
    add column if not exists recency_weighting_version varchar(80),
    add column if not exists season_window_key varchar(220),
    add column if not exists historical_depth_status varchar(40),
    add column if not exists market_specific_data_coverage varchar(120);

alter table team_feature_snapshots
    add column if not exists requested_season_count integer,
    add column if not exists actual_season_count_used integer,
    add column if not exists season_selection_mode varchar(40),
    add column if not exists selected_season_ids text,
    add column if not exists selected_season_names text,
    add column if not exists current_season_included boolean,
    add column if not exists fallback_applied boolean,
    add column if not exists oldest_data_date date,
    add column if not exists newest_data_date date,
    add column if not exists completed_matches_used integer,
    add column if not exists market_specific_usable_season_count integer,
    add column if not exists recency_weighting_version varchar(80),
    add column if not exists season_window_key varchar(220),
    add column if not exists historical_depth_status varchar(40),
    add column if not exists market_specific_data_coverage varchar(120);

alter table feature_generation_runs
    add column if not exists requested_season_count integer,
    add column if not exists actual_season_count_used integer,
    add column if not exists season_selection_mode varchar(40),
    add column if not exists selected_season_ids text,
    add column if not exists season_window_key varchar(220),
    add column if not exists fallback_applied boolean;

alter table prediction_selections
    add column if not exists requested_season_count integer,
    add column if not exists actual_season_count_used integer,
    add column if not exists selected_seasons text,
    add column if not exists completed_matches_used integer,
    add column if not exists fallback_applied boolean,
    add column if not exists historical_depth_status varchar(40),
    add column if not exists market_specific_data_coverage varchar(120),
    add column if not exists season_window_key varchar(220);

create index if not exists idx_league_baselines_window
    on league_baselines(league_id, calculation_date, season_window_key);

create index if not exists idx_team_features_window
    on team_feature_snapshots(league_id, team_id, calculation_date, season_window_key);

create index if not exists idx_prediction_selections_window
    on prediction_selections(season_window_key);

alter table league_baselines
    drop constraint if exists ux_league_baselines_league_season_date;

create unique index if not exists ux_league_baselines_league_date_window
    on league_baselines(league_id, calculation_date, coalesce(season_window_key, season_label));

alter table team_feature_snapshots
    drop constraint if exists ux_team_features_team_season_date;

create unique index if not exists ux_team_features_team_date_window
    on team_feature_snapshots(league_id, team_id, calculation_date, coalesce(season_window_key, season_label));
