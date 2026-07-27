-- Hibernate's @OrderColumn reorder strategy updates field_id in place for each fixed
-- position slot (one UPDATE per changed slot), so a swap transiently makes two rows for
-- the same version_id point at the same field_id before the second UPDATE resolves it.
-- A plain UNIQUE constraint checks immediately per-statement and rejects that intermediate
-- state; deferring the check to transaction commit lets the whole reorder complete first.
ALTER TABLE reference_data_object_version_field
    DROP CONSTRAINT reference_data_object_version_field_unique_field;

ALTER TABLE reference_data_object_version_field
    ADD CONSTRAINT reference_data_object_version_field_unique_field
        UNIQUE (version_id, field_id) DEFERRABLE INITIALLY DEFERRED;
