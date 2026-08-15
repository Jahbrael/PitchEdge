ALTER TABLE user_saved_batch_items
    ADD COLUMN match_id UUID,
    ADD COLUMN league_code VARCHAR(64),
    ADD COLUMN fixture VARCHAR(256),
    ADD COLUMN kickoff_at TIMESTAMPTZ,
    ADD COLUMN market_name VARCHAR(128),
    ADD COLUMN predicted_value VARCHAR(64),
    ADD COLUMN team_or_player VARCHAR(160),
    ADD COLUMN raw_model_probability NUMERIC(7,6),
    ADD COLUMN calibrated_probability NUMERIC(7,6),
    ADD COLUMN data_quality_score NUMERIC(7,6),
    ADD COLUMN calibration_status VARCHAR(32),
    ADD COLUMN bookmaker_implied_probability NUMERIC(7,6),
    ADD COLUMN probability_edge NUMERIC(8,6),
    ADD COLUMN ranking_score NUMERIC(9,6),
    ADD COLUMN reason VARCHAR(500),
    ADD COLUMN model_version VARCHAR(80);

ALTER TABLE user_saved_batch_items
    ALTER COLUMN prediction_selection_id DROP NOT NULL;

ALTER TABLE user_saved_batch_items
    DROP CONSTRAINT IF EXISTS user_saved_batch_items_prediction_selection_id_fkey;

ALTER TABLE user_saved_batch_items
    ADD CONSTRAINT fk_user_saved_batch_items_prediction_selection
        FOREIGN KEY (prediction_selection_id)
        REFERENCES prediction_selections(id)
        ON DELETE SET NULL;
