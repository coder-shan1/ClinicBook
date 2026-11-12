package com.clinicbook.seed;

import com.clinicbook.config.ClinicProperties;
import com.clinicbook.doctor.Doctor;
import com.clinicbook.doctor.DoctorRepository;
import com.clinicbook.doctor.Specialty;
import com.clinicbook.doctor.SpecialtyRepository;
import com.clinicbook.schedule.SlotGenerationService;
import com.clinicbook.schedule.TimeOff;
import com.clinicbook.schedule.TimeOffRepository;
import com.clinicbook.schedule.WorkingHours;
import com.clinicbook.schedule.WorkingHoursRepository;
import com.clinicbook.user.Role;
import com.clinicbook.user.User;
import com.clinicbook.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake demo data for the {@code local} profile only. It never runs in tests or
 * production, and it is idempotent so restarts do not duplicate doctors.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);
    private static final String DEMO_PASSWORD = "Doctor123!";

    private static final List<SpecialtySeed> SPECIALTIES = List.of(
            new SpecialtySeed("Cardiology", "Heart and vascular care"),
            new SpecialtySeed("Dermatology", "Skin, hair and nails"),
            new SpecialtySeed("Pediatrics", "Care for children and adolescents"),
            new SpecialtySeed("Orthopedics", "Bones, joints and muscles"),
            new SpecialtySeed("General Medicine", "Everyday adult primary care"),
            new SpecialtySeed("Neurology", "Brain, spine and nervous system"));

    private static final List<DoctorSeed> DOCTORS = List.of(
            new DoctorSeed("asha.verma@example.com", "Dr. Asha Verma", "+91 90000 00001",
                    "Cardiology", new BigDecimal("800.00"),
                    "Cardiologist focused on preventive heart care."),
            new DoctorSeed("rohan.mehta@example.com", "Dr. Rohan Mehta", "+91 90000 00002",
                    "Dermatology", new BigDecimal("600.00"),
                    "Dermatologist treating acne, eczema and pigmentation."),
            new DoctorSeed("priya.nair@example.com", "Dr. Priya Nair", "+91 90000 00003",
                    "Pediatrics", new BigDecimal("500.00"),
                    "Paediatrician with an interest in childhood asthma."),
            new DoctorSeed("vikram.rao@example.com", "Dr. Vikram Rao", "+91 90000 00004",
                    "Orthopedics", new BigDecimal("900.00"),
                    "Orthopaedic surgeon specialising in sports injuries."),
            new DoctorSeed("neha.kapoor@example.com", "Dr. Neha Kapoor", "+91 90000 00005",
                    "General Medicine", new BigDecimal("400.00"),
                    "General physician for routine and preventive care."),
            new DoctorSeed("arjun.singh@example.com", "Dr. Arjun Singh", "+91 90000 00006",
                    "Neurology", new BigDecimal("1000.00"),
                    "Neurologist evaluating headaches, seizures and neuropathy."));

    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final PasswordEncoder passwordEncoder;
    private final SlotGenerationService slotGenerationService;
    private final ClinicProperties clinicProperties;
    private final Clock clock;

    public LocalDataSeeder(UserRepository userRepository,
                           SpecialtyRepository specialtyRepository,
                           DoctorRepository doctorRepository,
                           WorkingHoursRepository workingHoursRepository,
                           TimeOffRepository timeOffRepository,
                           PasswordEncoder passwordEncoder,
                           SlotGenerationService slotGenerationService,
                           ClinicProperties clinicProperties,
                           Clock clock) {
        this.userRepository = userRepository;
        this.specialtyRepository = specialtyRepository;
        this.doctorRepository = doctorRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.passwordEncoder = passwordEncoder;
        this.slotGenerationService = slotGenerationService;
        this.clinicProperties = clinicProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Specialty> specialties = seedSpecialties();
        int created = 0;
        for (DoctorSeed seed : DOCTORS) {
            if (userRepository.existsByEmail(seed.email())) {
                continue;
            }
            User user = userRepository.save(new User(
                    seed.email(),
                    passwordEncoder.encode(DEMO_PASSWORD),
                    Role.DOCTOR,
                    seed.fullName(),
                    seed.phone()));
            Doctor doctor = doctorRepository.save(new Doctor(
                    user,
                    specialties.get(seed.specialtyName()),
                    seed.bio(),
                    seed.consultationFee()));

            workingHoursRepository.saveAll(defaultWorkingHours(doctor));
            if (created == 0) {
                seedTimeOff(doctor);
            }
            slotGenerationService.regenerate(doctor.getId());
            created++;
        }
        if (created > 0) {
            log.info("Seeded {} fake local doctors across {} specialties", created, specialties.size());
        }
    }

    private Map<String, Specialty> seedSpecialties() {
        Map<String, Specialty> byName = new LinkedHashMap<>();
        for (SpecialtySeed seed : SPECIALTIES) {
            Specialty specialty = specialtyRepository.findByNameIgnoreCase(seed.name())
                    .orElseGet(() -> specialtyRepository.save(new Specialty(seed.name(), seed.description())));
            byName.put(specialty.getName(), specialty);
        }
        return byName;
    }

    private List<WorkingHours> defaultWorkingHours(Doctor doctor) {
        List<WorkingHours> hours = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            hours.add(new WorkingHours(doctor, day, LocalTime.of(9, 0), LocalTime.of(13, 0), 30));
            hours.add(new WorkingHours(doctor, day, LocalTime.of(14, 0), LocalTime.of(17, 0), 30));
        }
        return hours;
    }

    private void seedTimeOff(Doctor doctor) {
        ZoneId zone = clinicProperties.zone();
        LocalDate tomorrow = LocalDate.now(clock.withZone(zone)).plusDays(1);
        Instant start = tomorrow.atTime(10, 0).atZone(zone).toInstant();
        if (timeOffRepository.existsByDoctorIdAndEndAtAfterAndStartAtBefore(
                doctor.getId(), start, start.plusSeconds(3600))) {
            return;
        }
        timeOffRepository.save(new TimeOff(doctor, start, start.plusSeconds(3600), "Demo: team meeting"));
    }

    private record SpecialtySeed(String name, String description) {
    }

    private record DoctorSeed(String email, String fullName, String phone, String specialtyName,
                              BigDecimal consultationFee, String bio) {
    }
}
