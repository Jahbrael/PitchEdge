update source_targets
set rate_limit_per_minute = 6,
    updated_at = now()
where source_type = 'ODDS_REFERENCE'
  and name like 'SharpAPI Upcoming Odds%';
