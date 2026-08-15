alter table automation_runs
    add column current_step varchar(64),
    add column completed_steps integer not null default 0,
    add column total_steps integer not null default 0;

alter table automation_runs
    add constraint ck_automation_runs_progress
        check (completed_steps >= 0 and total_steps >= 0 and completed_steps <= total_steps);
