alter table pipeline_runs
    alter column league_codes type text;

alter table automation_runs
    alter column league_codes type text;

alter table backtest_runs
    alter column league_codes type text;
