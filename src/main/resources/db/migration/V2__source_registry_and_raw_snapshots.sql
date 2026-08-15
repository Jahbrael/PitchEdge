create table source_targets (
    id uuid primary key,
    league_id uuid not null references leagues(id),
    source_type varchar(64) not null,
    name varchar(160) not null,
    url_template varchar(1000) not null,
    render_mode varchar(32) not null,
    active boolean not null,
    robots_txt_required boolean not null,
    user_agent varchar(160) not null,
    rate_limit_per_minute integer not null,
    timeout_ms integer not null,
    reliability_score numeric(5, 2) not null,
    selectors_json text,
    last_success_at timestamp(6) with time zone,
    last_failure_at timestamp(6) with time zone,
    consecutive_failures integer not null,
    last_failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_source_targets_league_type_name unique (league_id, source_type, name),
    constraint ck_source_targets_rate_limit check (rate_limit_per_minute between 1 and 120),
    constraint ck_source_targets_timeout check (timeout_ms between 1000 and 60000),
    constraint ck_source_targets_reliability check (reliability_score >= 0 and reliability_score <= 100),
    constraint ck_source_targets_failures check (consecutive_failures >= 0)
);

create index idx_source_targets_league_active
    on source_targets(league_id, active);

create index idx_source_targets_type_active
    on source_targets(source_type, active);

create index idx_source_targets_reliability
    on source_targets(reliability_score);

create table raw_snapshots (
    id uuid primary key,
    source_target_id uuid not null references source_targets(id),
    league_id uuid not null references leagues(id),
    data_refresh_log_id uuid references data_refresh_logs(id),
    snapshot_date date not null,
    source_url varchar(1200) not null,
    scrape_status varchar(32) not null,
    http_status_code integer,
    fetched_at timestamp(6) with time zone,
    duration_ms bigint,
    checksum_sha256 varchar(64) not null,
    content_type varchar(160),
    content_length bigint,
    response_headers_json text,
    raw_payload text,
    extracted_text text,
    error_message varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ux_raw_snapshots_target_date_checksum unique (source_target_id, snapshot_date, checksum_sha256),
    constraint ck_raw_snapshots_http_status check (http_status_code is null or http_status_code between 100 and 599),
    constraint ck_raw_snapshots_duration check (duration_ms is null or duration_ms >= 0),
    constraint ck_raw_snapshots_content_length check (content_length is null or content_length >= 0)
);

create index idx_raw_snapshots_target_date
    on raw_snapshots(source_target_id, snapshot_date);

create index idx_raw_snapshots_league_date_status
    on raw_snapshots(league_id, snapshot_date, scrape_status);

create index idx_raw_snapshots_refresh_log
    on raw_snapshots(data_refresh_log_id);
