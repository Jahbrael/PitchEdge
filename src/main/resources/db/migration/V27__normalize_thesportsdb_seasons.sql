create temporary table tmp_v27_future_thesportsdb_matches on commit drop as
select m.id
from matches m
join leagues l on l.id = m.league_id
where m.source_fixture_key like 'TSD:%'
  and m.season_label ~ '^[0-9]{4}[-/][0-9]{4}$'
  and substring(m.season_label from '([0-9]{4})$')::int > substring(l.current_season from '([0-9]{4})$')::int;

delete from prediction_selections
where match_id in (select id from tmp_v27_future_thesportsdb_matches);

delete from odds_snapshots
where match_id in (select id from tmp_v27_future_thesportsdb_matches);

delete from event_statistics
where match_id in (select id from tmp_v27_future_thesportsdb_matches);

delete from match_statistics
where match_id in (select id from tmp_v27_future_thesportsdb_matches);

delete from external_source_mappings
where source_type = 'THESPORTSDB'
  and entity_type in ('EVENT', 'FIXTURE')
  and internal_entity_id in (select id from tmp_v27_future_thesportsdb_matches);

delete from matches
where id in (select id from tmp_v27_future_thesportsdb_matches);

delete from league_season_market_availability bad
using league_season_market_availability good
where bad.league_id = good.league_id
  and bad.season_label ~ '^[0-9]{4}-[0-9]{4}$'
  and replace(bad.season_label, '-', '/') = good.season_label
  and bad.market_code = good.market_code;

delete from league_season_coverage bad
using league_season_coverage good
where bad.league_id = good.league_id
  and bad.season_label ~ '^[0-9]{4}-[0-9]{4}$'
  and replace(bad.season_label, '-', '/') = good.season_label;

update matches
set season_label = replace(season_label, '-', '/')
where season_label ~ '^[0-9]{4}-[0-9]{4}$';

update external_source_mappings
set season = replace(season, '-', '/')
where source_type = 'THESPORTSDB'
  and season ~ '^[0-9]{4}-[0-9]{4}$';

update source_targets
set source_season_token = replace(source_season_token, '-', '/')
where source_season_token ~ '^[0-9]{4}-[0-9]{4}$';

update source_targets
set target_season_label = replace(target_season_label, '-', '/')
where target_season_label ~ '^[0-9]{4}-[0-9]{4}$';

update league_season_coverage
set season_label = replace(season_label, '-', '/')
where season_label ~ '^[0-9]{4}-[0-9]{4}$';

update league_season_market_availability
set season_label = replace(season_label, '-', '/')
where season_label ~ '^[0-9]{4}-[0-9]{4}$';

update feature_generation_runs
set season_label = replace(season_label, '-', '/')
where season_label ~ '^[0-9]{4}-[0-9]{4}$';

update league_baselines
set season_label = replace(season_label, '-', '/')
where season_label ~ '^[0-9]{4}-[0-9]{4}$';

update team_feature_snapshots
set season_label = replace(season_label, '-', '/')
where season_label ~ '^[0-9]{4}-[0-9]{4}$';

update prediction_generation_runs
set feature_season_label = replace(feature_season_label, '-', '/')
where feature_season_label ~ '^[0-9]{4}-[0-9]{4}$';

update source_targets st
set active = false,
    system_disabled = true,
    health_note = 'Disabled by V27: TheSportsDB future season is beyond the configured current season.'
from leagues l
where st.league_id = l.id
  and st.target_season_label ~ '^[0-9]{4}[-/][0-9]{4}$'
  and substring(st.target_season_label from '([0-9]{4})$')::int > substring(l.current_season from '([0-9]{4})$')::int;
