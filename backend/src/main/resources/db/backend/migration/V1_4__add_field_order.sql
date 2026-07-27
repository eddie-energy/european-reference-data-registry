ALTER TABLE reference_data_object_version_field
    ADD COLUMN position INT;

WITH ordinals AS (
    SELECT version_id,
           field_id,
           ROW_NUMBER() OVER (PARTITION BY version_id ORDER BY ctid) - 1 AS ordinal
    FROM reference_data_object_version_field
)
UPDATE reference_data_object_version_field AS rdovf
SET position = ordinals.ordinal
FROM ordinals
WHERE rdovf.version_id = ordinals.version_id
  AND rdovf.field_id = ordinals.field_id;

ALTER TABLE reference_data_object_version_field
    ALTER COLUMN position SET NOT NULL;
