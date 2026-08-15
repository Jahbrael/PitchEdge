CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_users_username_normalized ON users (lower(username));

CREATE TABLE user_saved_batches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    batch_name VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_saved_batches_user ON user_saved_batches(user_id, created_at DESC);

CREATE TABLE user_saved_batch_items (
    id UUID PRIMARY KEY,
    user_saved_batch_id UUID NOT NULL REFERENCES user_saved_batches(id) ON DELETE CASCADE,
    prediction_selection_id UUID NOT NULL REFERENCES prediction_selections(id) ON DELETE CASCADE,
    market_code VARCHAR(64) NOT NULL,
    tuned_probability NUMERIC(7,6) NOT NULL,
    confidence_band VARCHAR(32) NOT NULL,
    model_quality_sample_size INTEGER,
    model_quality_calibration_error NUMERIC(7,6),
    decimal_odds NUMERIC(10,4),
    expected_value NUMERIC(10,6),
    value_rating VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_saved_batch_items_batch ON user_saved_batch_items(user_saved_batch_id);
