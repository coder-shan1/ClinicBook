package com.clinicbook.notification;

import com.clinicbook.appointment.Appointment;
import com.clinicbook.config.ClinicProperties;
import com.clinicbook.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds and sends HTML appointment emails. Delivery failures surface as runtime
 * exceptions so the caller can log them without affecting the appointment transaction.
 */
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm z", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer renderer;
    private final NotificationProperties properties;
    private final ClinicProperties clinicProperties;

    public EmailService(JavaMailSender mailSender,
                        EmailTemplateRenderer renderer,
                        NotificationProperties properties,
                        ClinicProperties clinicProperties) {
        this.mailSender = mailSender;
        this.renderer = renderer;
        this.properties = properties;
        this.clinicProperties = clinicProperties;
    }

    public void send(Appointment appointment, NotificationType type) {
        NotificationView view = view(appointment);
        String html = renderer.render(type, view);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(appointment.getPatient().getEmail());
            helper.setSubject(renderer.subject(type, view));
            helper.setText(html, true);
        } catch (MessagingException ex) {
            throw new MailPreparationException("Could not prepare " + type + " email", ex);
        }
        mailSender.send(message);
    }

    private NotificationView view(Appointment appointment) {
        return new NotificationView(
                appointment.getId(),
                appointment.getPatient().getFullName(),
                appointment.getDoctor().getUser().getFullName(),
                appointment.getDoctor().getSpecialty().getName(),
                DATE_FORMAT.format(appointment.getStartAt().atZone(clinicProperties.zone())),
                properties.clinicName());
    }
}
