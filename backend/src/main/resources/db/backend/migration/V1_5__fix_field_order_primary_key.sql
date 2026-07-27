-- Hibernate's @OrderColumn reorder strategy updates one row per position
-- ("UPDATE ... SET field_id = ? WHERE version_id = ? AND position = ?"), which is only
-- collision-free if (version_id, position) is itself the primary key. With the original
-- (version_id, field_id) primary key, reordering two already-linked fields transiently
-- violates it (the target field_id can already exist at another position for that version).
ALTER TABLE reference_data_object_version_field
    DROP CONSTRAINT reference_data_object_version_field_pkey;

ALTER TABLE reference_data_object_version_field
    ADD PRIMARY KEY (version_id, position);

ALTER TABLE reference_data_object_version_field
    ADD CONSTRAINT reference_data_object_version_field_unique_field UNIQUE (version_id, field_id);
