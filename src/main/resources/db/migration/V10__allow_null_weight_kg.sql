-- V10: Explicitly allow NULL weight_kg on set_logs for bodyweight exercises.
-- V5 created the column without NOT NULL, so this is defensive and idempotent
-- in PostgreSQL — a no-op if the constraint doesn't exist.
ALTER TABLE set_logs ALTER COLUMN weight_kg DROP NOT NULL;
