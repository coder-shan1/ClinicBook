CREATE TABLE appointment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    slot_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- One active appointment per slot. Cancelled appointments detach the slot
    -- (slot_id = NULL) so the slot can be booked again; MySQL allows many NULLs.
    CONSTRAINT uk_appointment_slot UNIQUE (slot_id),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT fk_appointment_slot FOREIGN KEY (slot_id) REFERENCES time_slot (id) ON DELETE SET NULL,
    INDEX idx_appointment_patient_start (patient_id, start_at),
    INDEX idx_appointment_doctor_start (doctor_id, start_at),
    INDEX idx_appointment_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
