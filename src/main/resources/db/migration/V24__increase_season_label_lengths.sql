alter table feature_generation_runs alter column season_label type varchar(128);
alter table league_baselines alter column season_label type varchar(128);
alter table team_feature_snapshots alter column season_label type varchar(128);
alter table prediction_generation_runs alter column feature_season_label type varchar(128);
