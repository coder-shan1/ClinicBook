package com.clinicbook.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    private static final NotificationView VIEW = new NotificationView(
            42L,
            "Patient One",
            "Dr. Test",
            "Cardiology",
            "Monday, 5 January 2026 at 09:00 IST",
            "ClinicBook Test");

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new EmailTemplateRenderer(engine);
    }

    @Test
    void rendersEveryTemplateWithTheAppointmentDetails() {
        for (NotificationType type : NotificationType.values()) {
            String html = renderer.render(type, VIEW);

            assertThat(html)
                    .as("template for %s", type)
                    .contains("Patient One")
                    .contains("Dr. Test")
                    .contains("Cardiology")
                    .contains("Monday, 5 January 2026 at 09:00 IST")
                    .contains("ClinicBook Test")
                    .doesNotContain("${")
                    .doesNotContain("th:text")
                    .doesNotContain("th:replace")
                    .doesNotContain("th:fragment");
        }
    }

    @Test
    void buildsATypeSpecificSubject() {
        assertThat(renderer.subject(NotificationType.BOOKING, VIEW)).contains("request received");
        assertThat(renderer.subject(NotificationType.CONFIRMATION, VIEW)).contains("confirmed");
        assertThat(renderer.subject(NotificationType.CANCELLATION, VIEW)).contains("cancelled");
        assertThat(renderer.subject(NotificationType.REMINDER, VIEW)).contains("Reminder");
    }
}
