CREATE TABLE reference_data_object
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT      NOT NULL,
    description TEXT      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE field
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT      NOT NULL,
    data_type  TEXT      NOT NULL,
    mandatory  BOOLEAN   NOT NULL,
    nation     TEXT      NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reference_data_object_version
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    major_version            INT       NOT NULL,
    minor_version            INT       NOT NULL,
    publish_state            TEXT      NOT NULL,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_data_object_id UUID      NOT NULL REFERENCES reference_data_object (id),
    UNIQUE (reference_data_object_id, major_version, minor_version)
);

CREATE INDEX IF NOT EXISTS idx_rdo_version_object_id
    ON reference_data_object_version (reference_data_object_id);

CREATE TABLE reference_data_object_version_field
(
    version_id UUID NOT NULL REFERENCES reference_data_object_version (id),
    field_id   UUID NOT NULL REFERENCES field (id),
    PRIMARY KEY (version_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_rdov_field_field_id
    ON reference_data_object_version_field (field_id);
