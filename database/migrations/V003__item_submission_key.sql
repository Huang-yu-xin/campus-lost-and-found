-- Apply once after V001. V002 is optional and is independent of this migration.
ALTER TABLE items
    ADD COLUMN submission_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN submission_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uq_items_publisher_submission (publisher_id, submission_key),
    ADD CONSTRAINT ck_items_submission_pair
        CHECK ((submission_key IS NULL) = (submission_hash IS NULL));
