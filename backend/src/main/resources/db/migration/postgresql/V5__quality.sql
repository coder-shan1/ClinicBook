-- Performance indexes for Phase 5 quality pass.
CREATE INDEX idx_time_slot_doctor_status_start ON time_slot (doctor_id, status, start_at);
CREATE INDEX idx_appointment_status_start ON appointment (status, start_at);
CREATE INDEX idx_appointment_doctor_status ON appointment (doctor_id, status);
CREATE INDEX idx_review_patient ON review (patient_id);
CREATE INDEX idx_user_role_enabled ON users (role, enabled);