update market_definitions
set code = 'YELLOW_CARDS_OVER_3_5',
    display_name = 'Yellow Cards Over 3.5',
    market_type = 'TOTAL_YELLOW_CARDS'
where code = 'YELLOW_CARDS_OVER';

update market_definitions
set code = 'CORNERS_OVER_8_5',
    display_name = 'Corners Over 8.5',
    market_type = 'TOTAL_CORNERS'
where code = 'CORNERS_OVER';

update market_definitions
set market_type = 'RED_CARD'
where code = 'RED_CARD_YES';

update user_saved_batch_items
set market_code = 'YELLOW_CARDS_OVER_3_5'
where market_code = 'YELLOW_CARDS_OVER';

update user_saved_batch_items
set market_code = 'CORNERS_OVER_8_5'
where market_code = 'CORNERS_OVER';

alter table market_definitions
    add column if not exists market_family varchar(64) not null default 'MATCH_RESULT',
    add column if not exists direction varchar(32) not null default 'YES',
    add column if not exists period varchar(32) not null default 'FULL_TIME',
    add column if not exists team_scope varchar(32) not null default 'MATCH',
    add column if not exists target_type varchar(32) not null default 'GOALS',
    add column if not exists requires_team_data boolean not null default true,
    add column if not exists requires_player_data boolean not null default false,
    add column if not exists requires_half_time_data boolean not null default false,
    add column if not exists requires_event_data boolean not null default false,
    add column if not exists requires_odds boolean not null default false,
    add column if not exists active boolean not null default true;

update market_definitions
set market_family = market_type;

create index if not exists idx_market_definitions_active_family
    on market_definitions(active, market_family);
