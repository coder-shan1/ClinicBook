package com.clinicbook.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByAppointmentIdAndType(Long appointmentId, NotificationType type);

    long countByAppointmentId(Long appointmentId);
}
