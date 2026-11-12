package com.clinicbook.schedule;

import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extends the rolling slot window once a night. Regenerating the whole horizon makes
 * the job self-healing: it adds the new day, drops expired free slots, and repairs any
 * drift caused by a missed run.
 */
@Component
public class SlotExtensionJob {

    private static final Logger log = LoggerFactory.getLogger(SlotExtensionJob.class);

    private final DoctorRepository doctorRepository;
    private final SlotGenerationService slotGenerationService;

    public SlotExtensionJob(DoctorRepository doctorRepository,
                            SlotGenerationService slotGenerationService) {
        this.doctorRepository = doctorRepository;
        this.slotGenerationService = slotGenerationService;
    }

    @Scheduled(cron = "${app.slot.nightly-cron:0 0 3 * * *}", zone = "${app.clinic.zone:UTC}")
    public void extendSlots() {
        List<Doctor> doctors = doctorRepository.findAllByActiveTrue();
        int created = 0;
        int removed = 0;
        for (Doctor doctor : doctors) {
            try {
                SlotGenerationResult result = slotGenerationService.regenerate(doctor.getId());
                created += result.created();
                removed += result.removed();
            } catch (RuntimeException ex) {
                // One bad schedule must not stop the rest of the clinic from being extended.
                log.error("Slot extension failed for doctor {}", doctor.getId(), ex);
            }
        }
        log.info("Nightly slot extension finished: doctors={}, created={}, removed={}",
                doctors.size(), created, removed);
    }
}
