create temporary table world_cup_stale_hist_match_map on commit drop as
with no_date_key as (
    select m.id,
           m.league_id,
           m.home_team_id,
           m.away_team_id,
           m.match_date,
           m.home_score,
           m.away_score,
           m.round_label,
           split_part(m.source_fixture_key, ':', 3) as sheet_name,
           split_part(m.source_fixture_key, ':', 4) as home_key,
           split_part(m.source_fixture_key, ':', 5) as away_key
    from matches m
    join leagues l on l.id = m.league_id
    where l.code = 'FIFA_WORLD_CUP_2026'
      and m.source_fixture_key ~ '^WC-HIST:FIFA_WORLD_CUP_2026:WorldCup(2014|2018|2022):[^:]+:[^:]+$'
),
date_key as (
    select m.id,
           m.league_id,
           m.home_team_id,
           m.away_team_id,
           m.match_date,
           m.home_score,
           m.away_score,
           m.round_label,
           split_part(m.source_fixture_key, ':', 3) as sheet_name,
           split_part(m.source_fixture_key, ':', 5) as home_key,
           split_part(m.source_fixture_key, ':', 6) as away_key
    from matches m
    join leagues l on l.id = m.league_id
    where l.code = 'FIFA_WORLD_CUP_2026'
      and m.source_fixture_key ~ '^WC-HIST:FIFA_WORLD_CUP_2026:WorldCup(2014|2018|2022):[0-9]{4}-[0-9]{2}-[0-9]{2}:[^:]+:[^:]+$'
),
ranked as (
    select n.id as stale_id,
           d.id as target_id,
           row_number() over (
               partition by n.id
               order by abs(d.match_date - n.match_date), d.match_date desc, d.id
           ) as match_rank
    from no_date_key n
    join date_key d on d.league_id = n.league_id
                   and d.home_team_id = n.home_team_id
                   and d.away_team_id = n.away_team_id
                   and d.home_score is not distinct from n.home_score
                   and d.away_score is not distinct from n.away_score
                   and d.round_label = n.round_label
                   and d.sheet_name = n.sheet_name
                   and d.home_key = n.home_key
                   and d.away_key = n.away_key
                   and abs(d.match_date - n.match_date) <= 2
                   and d.id <> n.id
)
select stale_id, target_id
from ranked
where match_rank = 1;

update external_source_mappings esm
set internal_entity_id = m.target_id
from world_cup_stale_hist_match_map m
where esm.internal_entity_id = m.stale_id;

update odds_snapshots os
set match_id = m.target_id
from world_cup_stale_hist_match_map m
where os.match_id = m.stale_id;

delete from prediction_selections ps
using world_cup_stale_hist_match_map m
where ps.match_id = m.stale_id
  and exists (
      select 1
      from prediction_selections target
      where target.match_id = m.target_id
        and target.market_definition_id = ps.market_definition_id
        and target.model_version = ps.model_version
  );

update prediction_selections ps
set match_id = m.target_id
from world_cup_stale_hist_match_map m
where ps.match_id = m.stale_id;

delete from match_statistics ms
using world_cup_stale_hist_match_map m
where ms.match_id = m.stale_id
  and exists (
      select 1
      from match_statistics target
      where target.match_id = m.target_id
  );

update match_statistics ms
set match_id = m.target_id
from world_cup_stale_hist_match_map m
where ms.match_id = m.stale_id;

delete from event_statistics es
using world_cup_stale_hist_match_map m
where es.match_id = m.stale_id
  and exists (
      select 1
      from event_statistics target
      where target.match_id = m.target_id
        and coalesce(target.team_id, '00000000-0000-0000-0000-000000000000'::uuid)
            = coalesce(es.team_id, '00000000-0000-0000-0000-000000000000'::uuid)
        and target.statistic_code = es.statistic_code
        and coalesce(target.period, 'FULL_TIME') = coalesce(es.period, 'FULL_TIME')
        and target.source_type = es.source_type
  );

update event_statistics es
set match_id = m.target_id
from world_cup_stale_hist_match_map m
where es.match_id = m.stale_id;

delete from matches m
using world_cup_stale_hist_match_map duplicate_map
where m.id = duplicate_map.stale_id;
