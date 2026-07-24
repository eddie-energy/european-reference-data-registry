CREATE TABLE entry
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_data_object_id UUID      NOT NULL REFERENCES reference_data_object (id) ON DELETE CASCADE,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entry_object_id
    ON entry (reference_data_object_id);

CREATE TABLE entry_value
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id       UUID      NOT NULL REFERENCES entry (id) ON DELETE CASCADE,
    field_id       UUID      NOT NULL REFERENCES field (id),
    text_value     TEXT      NULL,
    number_value   NUMERIC   NULL,
    date_value     DATE      NULL,
    enum_option_id UUID      NULL REFERENCES enum_option (id),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entry_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_entry_value_entry_id
    ON entry_value (entry_id);

CREATE INDEX IF NOT EXISTS idx_entry_value_field_id
    ON entry_value (field_id);
