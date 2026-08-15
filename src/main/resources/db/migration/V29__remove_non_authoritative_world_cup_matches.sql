create temporary table tmp_v29_non_authoritative_world_cup_matches on commit drop as
select m.id
from matches m
join leagues l on l.id = m.league_id
where l.code = 'FIFA_WORLD_CUP_2026'
  and m.source_fixture_key not like 'TSD:%';

delete from prediction_selections
where match_id in (select id from tmp_v29_non_authoritative_world_cup_matches);

delete from odds_snapshots
where match_id in (select id from tmp_v29_non_authoritative_world_cup_matches);

delete from event_statistics
where match_id in (select id from tmp_v29_non_authoritative_world_cup_matches);

delete from match_statistics
where match_id in (select id from tmp_v29_non_authoritative_world_cup_matches);

delete from external_source_mappings
where internal_entity_id in (select id from tmp_v29_non_authoritative_world_cup_matches)
  and entity_type in ('EVENT', 'FIXTURE');

delete from matches
where id in (select id from tmp_v29_non_authoritative_world_cup_matches);
