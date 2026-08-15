alter table prediction_generation_runs
    add column if not exists requested_season_count integer,
    add column if not exists actual_season_count_used integer,
    add column if not exists season_selection_mode varchar(40),
    add column if not exists selected_season_ids text,
    add column if not exists season_window_key varchar(220),
    add column if not exists fallback_applied boolean;

create index if not exists idx_prediction_runs_window
    on prediction_generation_runs(league_id, model_version, season_window_key, calculation_date, fixture_date_from, fixture_date_to, match_statuses, generation_status);
