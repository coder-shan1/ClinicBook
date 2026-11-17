CREATE TABLE notification_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- A given appointment receives each notification type at most once. This is the
    -- idempotency guard for the reminder job and makes lifecycle emails replay-safe.
    CONSTRAINT uk_notification_log_appointment_type UNIQUE (appointment_id, type),
    CONSTRAINT fk_notification_log_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id) ON DELETE CASCADE,
    INDEX idx_notification_log_sent_at (sent_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- One review per appointment; the database is the final guard against duplicates.
    CONSTRAINT uk_review_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_patient FOREIGN KEY (patient_id) REFERENCES users (id),
    CONSTRAINT fk_review_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id) ON DELETE CASCADE,
    INDEX idx_review_doctor_created (doctor_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE doctor
    ADD COLUMN rating_average DECIMAL(3, 2) NULL,
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;
