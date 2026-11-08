package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Con un JobService simulado: nunca se llama a Arbeitnow. */
class ArbeitnowAutoSyncTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Instant SEED_DATE = Instant.parse("2026-10-03T02:16:22Z");
    private static final Duration RETRY = Duration.ofHours(6);

    private final JobService jobs = mock(JobService.class);

    @BeforeEach
    void setUp() {
        when(jobs.syncSource("Arbeitnow")).thenReturn(new JobSyncReport(List.of(), 0, false));
    }

    private void lastSyncs(Instant arbeitnow, Instant remotive) {
        when(jobs.getStats()).thenReturn(new JobStats(0, List.of(
                new JobStats.SourceStats("Remotive", 0, remotive, null),
                new JobStats.SourceStats("Arbeitnow", 0, arbeitnow, null)), remotive));
    }

    private ArbeitnowAutoSync autoSync(int intervalDays, Instant now) {
        return new ArbeitnowAutoSync(jobs, Clock.fixed(now, ZoneOffset.UTC), intervalDays, RETRY, Runnable::run);
    }

    @Test
    void installWithSeedDateSyncsArbeitnowOnStart() {
        // Lo que pasaba: Remotive al día, Arbeitnow con la fecha de las ofertas incluidas (2 oct).
        lastSyncs(SEED_DATE, NOW.minus(Duration.ofHours(1)));

        autoSync(1, NOW).checkOnStart();

        verify(jobs).syncSource("Arbeitnow");
        verify(jobs, never()).syncSource("Remotive");
        verify(jobs, never()).sync();
    }

    @Test
    void doesNotSyncWithinTheInterval() {
        lastSyncs(NOW.minus(Duration.ofHours(23)), NOW);

        autoSync(1, NOW).check();

        verify(jobs, never()).syncSource("Arbeitnow");
    }

    @Test
    void syncsWhenNeverSynced() {
        lastSyncs(null, NOW);

        autoSync(1, NOW).check();

        verify(jobs).syncSource("Arbeitnow");
    }

    @Test
    void startupCheckRunsInBackground() {
        lastSyncs(SEED_DATE, NOW);
        List<Runnable> scheduled = new ArrayList<>();
        var autoSync = new ArbeitnowAutoSync(jobs, Clock.fixed(NOW, ZoneOffset.UTC), 1, RETRY, scheduled::add);

        autoSync.checkOnStart();

        verify(jobs, never()).syncSource("Arbeitnow");
        assertThat(scheduled).hasSize(1);
        scheduled.get(0).run();
        verify(jobs).syncSource("Arbeitnow");
    }

    @Test
    void afterAFailureWaitsBeforeRetrying() {
        lastSyncs(SEED_DATE, NOW);
        when(jobs.syncSource("Arbeitnow")).thenThrow(new JobSourceUnavailableException("Arbeitnow no responde"));
        var autoSync = autoSync(1, NOW);

        assertThatCode(autoSync::check).doesNotThrowAnyException();
        autoSync.check(); // dentro de las 6 h: no vuelve a intentar

        verify(jobs, times(1)).syncSource("Arbeitnow");
    }

    @Test
    void intervalOutsideOneToFiveIsClamped() {
        assertThat(autoSync(0, NOW).intervalDays()).isEqualTo(1);
        assertThat(autoSync(1, NOW).intervalDays()).isEqualTo(1);
        assertThat(autoSync(30, NOW).intervalDays()).isEqualTo(5);
        assertThat(autoSync(1, NOW).source()).isEqualTo("Arbeitnow");
    }
}
