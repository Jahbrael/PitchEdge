create table if not exists prediction_form_runs (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    request_id uuid not null,
    generated_at timestamp with time zone not null,
    fixture_date_from date,
    fixture_date_to date,
    model_version varchar(80),
    strategy varchar(40),
    fixtures_considered integer,
    returned_selections integer,
    status varchar(40),
    response_json text not null,
    constraint ux_prediction_form_runs_request_id unique (request_id)
);

create index if not exists idx_prediction_form_runs_generated_at
    on prediction_form_runs (generated_at);

create index if not exists idx_prediction_form_runs_fixture_window
    on prediction_form_runs (fixture_date_from, fixture_date_to);
