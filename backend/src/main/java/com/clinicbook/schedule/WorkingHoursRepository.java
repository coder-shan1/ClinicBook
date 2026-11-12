package com.clinicbook.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

    void deleteAllByDoctorId(Long doctorId);
}
