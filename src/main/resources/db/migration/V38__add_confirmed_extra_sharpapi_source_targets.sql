with sharpapi_targets(code, sport_key) as (
    values
    ('RUSSIAN_FOOTBALL_PREMIER_LEAGUE', 'russia_-_premier_league'),
    ('ITALIAN_SERIE_B', 'italy_-_serie_b'),
    ('ENGLISH_LEAGUE_1', 'england_-_league_1'),
    ('ENGLISH_LEAGUE_2', 'england_-_league_2'),
    ('GERMAN_2_BUNDESLIGA', 'germany_-_bundesliga_2'),
    ('SWEDISH_SUPERETTAN', 'sweden_-_superettan'),
    ('UEFA_NATIONS_LEAGUE', 'uefa_-_nations_league'),
    ('COPA_ARGENTINA', 'argentina_-_copa_argentina'),
    ('UEFA_SUPER_CUP', 'uefa_-_super_cup'),
    ('VENEZUELA_PRIMERA_DIVISION', 'venezuela_-_primera_division'),
    ('AMERICAN_NWSL', 'usa_-_nwsl'),
    ('FA_COMMUNITY_SHIELD', 'england_-_community_shield'),
    ('ARGENTINIAN_PRIMERA_B_NACIONAL', 'argentina_-_primera_nacional'),
    ('CHINA_LEAGUE_ONE', 'china_-_league_one'),
    ('BOLIVIAN_PRIMERA_DIVISION', 'bolivia_-_primera_division'),
    ('COPA_DO_BRASIL', 'brazil_-_copa_do_brasil')
), active_state(active) as (
    select coalesce(bool_or(st.active), true)
    from source_targets st
    where st.source_type = 'ODDS_REFERENCE'
      and st.name like 'SharpAPI Upcoming Odds%'
)
insert into source_targets (
    id,
    league_id,
    source_type,
    name,
    url_template,
    source_season_token,
    target_season_label,
    render_mode,
    active,
    robots_txt_required,
    user_agent,
    rate_limit_per_minute,
    timeout_ms,
    reliability_score,
    fallback_priority,
    selectors_json,
    last_success_at,
    last_failure_at,
    consecutive_failures,
    last_failure_reason,
    system_disabled,
    quarantined_until,
    created_at,
    updated_at
)
select
    gen_random_uuid(),
    l.id,
    'ODDS_REFERENCE',
    'SharpAPI Upcoming Odds ' || l.name || ' JSON',
    '{sharpApiBaseUrl}/odds?league=' || t.sport_key || '&limit=200',
    null,
    l.current_season,
    'STATIC_HTML',
    a.active,
    false,
    'BetAIResearchBot/0.1 (+local-development)',
    6,
    20000,
    92.00,
    5,
    '{"format":"sharpapi-odds-json","sportKey":"' || t.sport_key || '","includeOneXTwo":true,"includeOverUnder25":true}',
    null,
    null,
    0,
    null,
    false,
    null,
    now(),
    now()
from sharpapi_targets t
join leagues l on l.code = t.code
cross join active_state a
on conflict (league_id, source_type, name) do update
set url_template = excluded.url_template,
    target_season_label = excluded.target_season_label,
    render_mode = excluded.render_mode,
    active = excluded.active,
    robots_txt_required = excluded.robots_txt_required,
    user_agent = excluded.user_agent,
    rate_limit_per_minute = excluded.rate_limit_per_minute,
    timeout_ms = excluded.timeout_ms,
    reliability_score = excluded.reliability_score,
    fallback_priority = excluded.fallback_priority,
    selectors_json = excluded.selectors_json,
    consecutive_failures = 0,
    last_failure_reason = null,
    system_disabled = false,
    quarantined_until = null,
    updated_at = now();
