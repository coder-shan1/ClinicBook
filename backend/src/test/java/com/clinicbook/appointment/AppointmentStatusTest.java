package com.clinicbook.appointment;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusTest {

    @Test
    void allowsOnlyTheDocumentedTransitions() {
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CONFIRMED)).isTrue();
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CANCELLED)).isTrue();

        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.COMPLETED)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.NO_SHOW)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.CANCELLED)).isTrue();
    }

    @Test
    void everyOtherPairIsRejected() {
        Set<AppointmentTransition> allowed = Set.of(
                new AppointmentTransition(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                new AppointmentTransition(AppointmentStatus.PENDING, AppointmentStatus.CANCELLED),
                new AppointmentTransition(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED),
                new AppointmentTransition(AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW),
                new AppointmentTransition(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED));

        for (AppointmentStatus from : AppointmentStatus.values()) {
            for (AppointmentStatus to : AppointmentStatus.values()) {
                AppointmentTransition pair = new AppointmentTransition(from, to);
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(allowed.contains(pair));
            }
        }
    }

    @Test
    void terminalStatusesNeverMove() {
        for (AppointmentStatus terminal : EnumSet.of(
                AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)) {
            assertThat(terminal.isTerminal()).isTrue();
            assertThat(terminal.isActive()).isFalse();
            for (AppointmentStatus target : AppointmentStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void pendingAndConfirmedAreActive() {
        assertThat(AppointmentStatus.PENDING.isActive()).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.isActive()).isTrue();
        assertThat(AppointmentStatus.PENDING.isTerminal()).isFalse();
        assertThat(AppointmentStatus.CONFIRMED.isTerminal()).isFalse();
    }

    private record AppointmentTransition(AppointmentStatus from, AppointmentStatus to) {
    }
}
