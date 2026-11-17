package com.clinicbook.doctor;

import com.clinicbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "doctor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(length = 1000)
    private String bio;

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "rating_average", precision = 3, scale = 2)
    private BigDecimal ratingAverage;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Doctor(User user, Specialty specialty, String bio, BigDecimal consultationFee) {
        this.user = user;
        this.specialty = specialty;
        this.bio = bio;
        this.consultationFee = consultationFee;
        this.active = true;
    }

    public void update(Specialty specialty, String bio, BigDecimal consultationFee) {
        this.specialty = specialty;
        this.bio = bio;
        this.consultationFee = consultationFee;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    /**
     * Folds a new rating into the running average. Callers lock the doctor row so
     * concurrent reviews cannot lose an update.
     */
    public void addRating(int rating) {
        BigDecimal previousTotal = ratingAverage == null
                ? BigDecimal.ZERO
                : ratingAverage.multiply(BigDecimal.valueOf(ratingCount));
        int updatedCount = ratingCount + 1;
        this.ratingAverage = previousTotal
                .add(BigDecimal.valueOf(rating))
                .divide(BigDecimal.valueOf(updatedCount), 2, RoundingMode.HALF_UP);
        this.ratingCount = updatedCount;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
