ALTER TABLE entry
    RENAME TO reference_data_entry;

ALTER TABLE reference_data_entry
    RENAME CONSTRAINT entry_pkey TO pk_reference_data_entry;

ALTER TABLE reference_data_entry
    RENAME CONSTRAINT entry_reference_data_object_id_fkey
        TO fk_reference_data_entry_reference_data_object;

ALTER INDEX idx_entry_object_id
    RENAME TO idx_reference_data_entry_reference_data_object_id;

ALTER TABLE entry_value
    RENAME TO reference_data_entry_value;

ALTER TABLE reference_data_entry_value
    RENAME COLUMN entry_id TO reference_data_entry_id;

ALTER TABLE reference_data_entry_value
    RENAME CONSTRAINT entry_value_pkey TO pk_reference_data_entry_value;

ALTER TABLE reference_data_entry_value
    RENAME CONSTRAINT entry_value_entry_id_fkey
        TO fk_reference_data_entry_value_reference_data_entry;

ALTER TABLE reference_data_entry_value
    RENAME CONSTRAINT entry_value_field_id_fkey
        TO fk_reference_data_entry_value_field;

ALTER TABLE reference_data_entry_value
    RENAME CONSTRAINT entry_value_enum_option_id_fkey
        TO fk_reference_data_entry_value_enum_option;

ALTER TABLE reference_data_entry_value
    RENAME CONSTRAINT entry_value_entry_id_field_id_key
        TO uq_reference_data_entry_value_reference_data_entry_field;

ALTER INDEX idx_entry_value_entry_id
    RENAME TO idx_reference_data_entry_value_reference_data_entry_id;

ALTER INDEX idx_entry_value_field_id
    RENAME TO idx_reference_data_entry_value_field_id;
