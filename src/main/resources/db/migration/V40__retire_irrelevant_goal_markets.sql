update market_definitions
set enabled = false,
    active = false,
    updated_at = current_timestamp
where code in (
    'UNDER_0_5_GOALS',
    'OVER_5_5_GOALS',
    'UNDER_5_5_GOALS',
    'OVER_6_5_GOALS',
    'UNDER_6_5_GOALS',
    'OVER_7_5_GOALS',
    'UNDER_7_5_GOALS',
    'HOME_TEAM_OVER_3_5_GOALS',
    'HOME_TEAM_OVER_4_5_GOALS',
    'HOME_TEAM_UNDER_3_5_GOALS',
    'HOME_TEAM_UNDER_4_5_GOALS',
    'AWAY_TEAM_OVER_3_5_GOALS',
    'AWAY_TEAM_OVER_4_5_GOALS',
    'AWAY_TEAM_UNDER_3_5_GOALS',
    'AWAY_TEAM_UNDER_4_5_GOALS'
);
