CREATE TABLE enum_option
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id   UUID      NOT NULL REFERENCES field (id) ON DELETE CASCADE,
    name       TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (field_id, name)
);

CREATE INDEX IF NOT EXISTS idx_enum_option_field_id
    ON enum_option (field_id);
