alter table source_targets
    add column source_season_token varchar(32),
    add column target_season_label varchar(32);

create index idx_source_targets_target_season_active
    on source_targets(target_season_label, active);

alter table prediction_generation_runs
    add column feature_season_label varchar(32);

update prediction_generation_runs pgr
set feature_season_label = leagues.current_season
from leagues
where pgr.league_id = leagues.id
  and pgr.feature_season_label is null;

alter table prediction_generation_runs
    alter column feature_season_label set not null;

drop index if exists idx_prediction_runs_league_model_dates_status;

create index idx_prediction_runs_league_model_feature_dates_status
    on prediction_generation_runs(
        league_id,
        model_version,
        feature_season_label,
        calculation_date,
        fixture_date_from,
        fixture_date_to,
        match_statuses,
        generation_status
    );
