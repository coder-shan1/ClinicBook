package com.clinicbook.schedule;

import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StartupSlotGenerator implements ApplicationListener<ApplicationReadyEvent> {

    private final DoctorRepository doctorRepository;
    private final SlotGenerationService slotGenerationService;

    public StartupSlotGenerator(DoctorRepository doctorRepository,
                                SlotGenerationService slotGenerationService) {
        this.doctorRepository = doctorRepository;
        this.slotGenerationService = slotGenerationService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<Doctor> doctors = doctorRepository.findAllByActiveTrue();
        for (Doctor doctor : doctors) {
            try {
                slotGenerationService.regenerate(doctor.getId());
            } catch (RuntimeException ex) {
                // One bad schedule must not block startup of other doctors.
            }
        }
    }
}
