update source_targets
set active = false,
    system_disabled = true,
    health_note = 'Disabled by V26: production football data uses TheSportsDB Premium; odds use The Odds API.',
    last_failure_reason = null,
    quarantined_until = null
where not (
    lower(coalesce(name, '')) like '%thesportsdb%'
    or lower(coalesce(url_template, '')) like '%thesportsdb.com%'
    or lower(coalesce(selectors_json, '')) like '%thesportsdb%'
    or lower(coalesce(name, '')) like '%the odds api%'
    or lower(coalesce(url_template, '')) like '%theoddsapi%'
    or lower(coalesce(url_template, '')) like '%the-odds-api%'
    or lower(coalesce(selectors_json, '')) like '%the-odds-api%'
);
