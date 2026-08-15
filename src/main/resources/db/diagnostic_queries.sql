-- Diagnostic queries to detect data duplication that violates application uniqueness assumptions

-- 1. Matches duplicate by source_fixture_key
SELECT league_id, source_fixture_key, COUNT(*) as duplicate_count
FROM matches
GROUP BY league_id, source_fixture_key
HAVING COUNT(*) > 1;

-- 2. Matches duplicate by Teams and Kickoff Time
SELECT league_id, home_team_id, away_team_id, kickoff_at, COUNT(*) as duplicate_count
FROM matches
GROUP BY league_id, home_team_id, away_team_id, kickoff_at
HAVING COUNT(*) > 1;

-- 3. Matches duplicate by Teams and Date
SELECT league_id, home_team_id, away_team_id, match_date, COUNT(*) as duplicate_count
FROM matches
GROUP BY league_id, home_team_id, away_team_id, match_date
HAVING COUNT(*) > 1;

-- 4. Teams duplicate by Canonical Name (Case Insensitive)
SELECT league_id, LOWER(canonical_name) as canonical_name_lower, COUNT(*) as duplicate_count
FROM teams
GROUP BY league_id, LOWER(canonical_name)
HAVING COUNT(*) > 1;

-- 5. External Source Mappings duplicate by internal entity ID (Where uniqueness is implicitly expected but not enforced)
SELECT source_type, entity_type, internal_entity_id, COUNT(*) as duplicate_count
FROM external_source_mappings
WHERE internal_entity_id IS NOT NULL
GROUP BY source_type, entity_type, internal_entity_id
HAVING COUNT(*) > 1;
