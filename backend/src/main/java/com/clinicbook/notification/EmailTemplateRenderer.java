package com.clinicbook.notification;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

/**
 * Renders the HTML email bodies. Kept separate from sending so templates can be tested
 * without an SMTP server.
 */
@Component
public class EmailTemplateRenderer {

    private final SpringTemplateEngine templateEngine;

    public EmailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(NotificationType type, NotificationView view) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("view", view);
        return templateEngine.process("email/" + templateName(type), context);
    }

    public String subject(NotificationType type, NotificationView view) {
        return switch (type) {
            case BOOKING -> "Appointment request received - " + view.clinicName();
            case CONFIRMATION -> "Your appointment is confirmed - " + view.clinicName();
            case CANCELLATION -> "Your appointment was cancelled - " + view.clinicName();
            case REMINDER -> "Reminder: your appointment is coming up - " + view.clinicName();
        };
    }

    private static String templateName(NotificationType type) {
        return switch (type) {
            case BOOKING -> "booking";
            case CONFIRMATION -> "confirmation";
            case CANCELLATION -> "cancellation";
            case REMINDER -> "reminder";
        };
    }
}
