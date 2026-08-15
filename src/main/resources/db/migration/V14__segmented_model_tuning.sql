alter table model_tuning_profiles
    add column segment_key varchar(64) not null default 'GLOBAL';

alter table model_tuning_profiles
    drop constraint ux_model_tuning_profile;

alter table model_tuning_profiles
    add constraint ux_model_tuning_profile
        unique (league_id, market_definition_id, model_version, profile_date, segment_key);

create index idx_model_tuning_segment_lookup
    on model_tuning_profiles(league_id, market_definition_id, model_version, segment_key, profile_date desc, active);
