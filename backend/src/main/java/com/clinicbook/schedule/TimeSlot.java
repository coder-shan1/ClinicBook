package com.clinicbook.schedule;

import com.clinicbook.doctor.Doctor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "time_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimeSlotStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    public TimeSlot(Doctor doctor, Instant startAt, Instant endAt) {
        this.doctor = doctor;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = TimeSlotStatus.AVAILABLE;
    }

    /**
     * Slot length can change when a doctor edits working hours. Only free slots are
     * resized; booked slots keep the duration that was agreed with the patient.
     */
    public void resize(Instant endAt) {
        if (status != TimeSlotStatus.AVAILABLE) {
            throw new IllegalStateException("Only available slots can be resized");
        }
        this.endAt = endAt;
    }
}
