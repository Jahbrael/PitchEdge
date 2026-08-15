alter table matches
    add column if not exists home_half_time_score integer,
    add column if not exists away_half_time_score integer,
    add column if not exists referee varchar(160);

alter table match_statistics
    add column if not exists home_expected_goals numeric(5, 2),
    add column if not exists away_expected_goals numeric(5, 2),
    add column if not exists home_possession integer,
    add column if not exists away_possession integer;
