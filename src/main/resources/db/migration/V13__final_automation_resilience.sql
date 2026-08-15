alter table source_targets
    add column fallback_priority integer not null default 100,
    add column system_disabled boolean not null default false,
    add column quarantined_until timestamp(6) with time zone,
    add column health_note varchar(1000);

alter table source_targets
    add constraint ck_source_targets_fallback_priority check (fallback_priority between 1 and 1000);

create index idx_source_targets_resilience
    on source_targets(league_id, active, system_disabled, quarantined_until, fallback_priority, reliability_score desc);

create table automation_runs (
    id uuid primary key,
    automation_date date not null,
    trigger_type varchar(32) not null,
    league_codes varchar(500) not null,
    model_version varchar(80) not null,
    run_status varchar(32) not null,
    started_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    duration_ms bigint,
    attempt_count integer not null,
    step_summary_json text,
    warning_count integer not null,
    failure_reason varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint ck_automation_runs_duration check (duration_ms is null or duration_ms >= 0),
    constraint ck_automation_runs_counts check (attempt_count >= 0 and warning_count >= 0)
);

create index idx_automation_runs_date_status
    on automation_runs(automation_date, run_status);

create index idx_automation_runs_started_at
    on automation_runs(started_at desc);
