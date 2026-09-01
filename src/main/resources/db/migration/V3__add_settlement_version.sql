
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name  = 'settlement_records'
          AND column_name = 'version'
    ) THEN
        ALTER TABLE settlement_records
            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;
