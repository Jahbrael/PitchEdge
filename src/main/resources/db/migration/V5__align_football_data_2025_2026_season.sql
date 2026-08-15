update leagues
set current_season = '2025/2026',
    updated_at = now()
where code in ('PREMIER_LEAGUE', 'LA_LIGA', 'SERIE_A')
  and current_season = '2026/2027';

update matches
set season_label = '2025/2026',
    updated_at = now()
where season_label = '2026/2027'
  and source_fixture_key like 'FD:%';

update feature_generation_runs
set season_label = '2025/2026',
    updated_at = now()
where season_label = '2026/2027';

update league_baselines
set season_label = '2025/2026',
    updated_at = now()
where season_label = '2026/2027';

update team_feature_snapshots
set season_label = '2025/2026',
    updated_at = now()
where season_label = '2026/2027';
