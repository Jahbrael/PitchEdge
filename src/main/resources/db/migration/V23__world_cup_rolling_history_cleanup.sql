update matches m
set season_label = '2026_QUALIFIERS'
from leagues l
where m.league_id = l.id
  and l.code = 'FIFA_WORLD_CUP_2026'
  and m.round_label = 'World Cup 2026 Qualifier';

update matches m
set season_label = substring(m.round_label from 'WorldCup([0-9]{4})')
from leagues l
where m.league_id = l.id
  and l.code = 'FIFA_WORLD_CUP_2026'
  and m.round_label ~ '^WorldCup[0-9]{4}$';

create temporary table world_cup_duplicate_matches_to_delete on commit drop as
with ranked as (
    select m.id,
           row_number() over (
               partition by m.league_id,
                            m.round_label,
                            m.home_team_id,
                            m.away_team_id,
                            m.home_score,
                            m.away_score
               order by m.match_date desc, m.kickoff_at desc, m.id desc
           ) as duplicate_rank
    from matches m
    join leagues l on l.id = m.league_id
    where l.code = 'FIFA_WORLD_CUP_2026'
      and m.source_fixture_key like 'WC-HIST:%'
      and m.round_label ~ '^WorldCup(2014|2018|2022)$'
)
select id
from ranked
where duplicate_rank > 1;

update prediction_selections ps
set best_odds_snapshot_id = null
where ps.best_odds_snapshot_id in (
    select os.id
    from odds_snapshots os
    join world_cup_duplicate_matches_to_delete d on d.id = os.match_id
);

delete from prediction_selections ps
using world_cup_duplicate_matches_to_delete d
where ps.match_id = d.id;

delete from odds_snapshots os
using world_cup_duplicate_matches_to_delete d
where os.match_id = d.id;

delete from event_statistics es
using world_cup_duplicate_matches_to_delete d
where es.match_id = d.id;

delete from match_statistics ms
using world_cup_duplicate_matches_to_delete d
where ms.match_id = d.id;

delete from matches m
using world_cup_duplicate_matches_to_delete d
where m.id = d.id;
