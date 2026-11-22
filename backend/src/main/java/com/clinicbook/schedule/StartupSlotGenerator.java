package com.clinicbook.schedule;

import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StartupSlotGenerator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupSlotGenerator.class);

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
        log.info("StartupSlotGenerator: found {} active doctors", doctors.size());
        int totalCreated = 0;
        for (Doctor doctor : doctors) {
            try {
                SlotGenerationResult result = slotGenerationService.regenerate(doctor.getId());
                log.info("StartupSlotGenerator: doctor {} created={} removed={} resized={} available={}",
                        doctor.getId(), result.created(), result.removed(), result.resized(), result.available());
                totalCreated += result.created();
            } catch (RuntimeException ex) {
                log.error("StartupSlotGenerator: slot generation failed for doctor {}", doctor.getId(), ex);
            }
        }
        log.info("StartupSlotGenerator: total slots created={}", totalCreated);
    }
}
