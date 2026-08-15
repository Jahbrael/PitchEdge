alter table matches
    add column if not exists live_minute varchar(32),
    add column if not exists score_refreshed_at timestamp with time zone;
