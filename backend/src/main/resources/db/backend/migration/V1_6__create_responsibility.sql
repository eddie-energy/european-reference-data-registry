CREATE TABLE reference_data_responsibility
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_data_object_id UUID NOT NULL REFERENCES reference_data_object (id) ON DELETE CASCADE,
    organization_id          UUID NOT NULL,
    nation                   TEXT NOT NULL,
    UNIQUE (reference_data_object_id, organization_id, nation)
);

CREATE INDEX idx_responsibility_organization ON reference_data_responsibility (organization_id);
