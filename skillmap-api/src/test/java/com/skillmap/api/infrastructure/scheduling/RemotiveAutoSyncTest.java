package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.exception.JobSourceRateLimitException;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Con un JobService falso y un reloj simulado: nunca se llama a Remotive ni a Arbeitnow. */
class RemotiveAutoSyncTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Duration RETRY = Duration.ofHours(6);

    private final FakeJobService jobs = new FakeJobService();
    private final MutableClock clock = new MutableClock(NOW);

    private RemotiveAutoSync autoSync(int intervalDays) {
        return new RemotiveAutoSync(jobs, clock, intervalDays, RETRY, Runnable::run);
    }

    @Test
    void doesNotSyncWhenLessThanIntervalDaysHavePassed() {
        jobs.remotiveLastSync = NOW.minus(Duration.ofDays(4)).plusSeconds(1);

        autoSync(4).check();

        assertThat(jobs.syncedSources).isEmpty();
    }

    @Test
    void syncsOnlyRemotiveOnceIntervalDaysHavePassed() {
        jobs.remotiveLastSync = NOW.minus(Duration.ofDays(4));

        autoSync(4).check();

        assertThat(jobs.syncedSources).containsExactly("Remotive");
        assertThat(jobs.fullSyncs).as("no sincroniza las demás fuentes").isZero();
    }

    @Test
    void syncsWhenRemotiveWasNeverSynced() {
        autoSync(4).check();

        assertThat(jobs.syncedSources).containsExactly("Remotive");
    }

    @Test
    void startupCheckRunsInBackground() {
        jobs.remotiveLastSync = NOW.minus(Duration.ofDays(10));
        List<Runnable> scheduled = new ArrayList<>();
        var autoSync = new RemotiveAutoSync(jobs, clock, 4, RETRY, scheduled::add);

        autoSync.checkOnStart();

        assertThat(jobs.syncedSources).as("no bloquea el arranque").isEmpty();
        assertThat(scheduled).hasSize(1);
        scheduled.get(0).run();
        assertThat(jobs.syncedSources).containsExactly("Remotive");
    }

    @Test
    void failureDoesNotThrow() {
        jobs.failure = new JobSourceRateLimitException("Remotive respondió 429");

        assertThatCode(() -> autoSync(4).check()).doesNotThrowAnyException();
        assertThat(jobs.syncedSources).containsExactly("Remotive");
    }

    @Test
    void afterAFailureWaitsSixHoursBeforeRetrying() {
        jobs.failure = new JobSourceUnavailableException("Remotive no responde");
        var autoSync = autoSync(4);
        autoSync.check();

        clock.now = NOW.plus(RETRY).minusSeconds(1);
        autoSync.check();
        assertThat(jobs.syncedSources).as("dentro de las 6 h").hasSize(1);

        jobs.failure = null;
        clock.now = NOW.plus(RETRY);
        autoSync.check();
        assertThat(jobs.syncedSources).hasSize(2);
    }

    @Test
    void intervalOutsideThreeToFiveIsClamped() {
        assertThat(autoSync(1).intervalDays()).isEqualTo(3);
        assertThat(autoSync(4).intervalDays()).isEqualTo(4);
        assertThat(autoSync(30).intervalDays()).isEqualTo(5);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class FakeJobService implements JobService {
        private Instant remotiveLastSync;
        private RuntimeException failure;
        private final List<String> syncedSources = new ArrayList<>();
        private int fullSyncs;

        @Override
        public JobSyncReport sync() {
            fullSyncs++;
            return new JobSyncReport(List.of(), 0, false);
        }

        @Override
        public JobSyncReport syncSource(String source) {
            syncedSources.add(source);
            if (failure != null) {
                throw failure;
            }
            return new JobSyncReport(List.of(), 0, false);
        }

        @Override
        public List<JobPosting> getNewestJobs(int limit) {
            return List.of();
        }

        @Override
        public JobPosting getJob(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobStats getStats() {
            return new JobStats(0, List.of(
                    new JobStats.SourceStats("Arbeitnow", 0, NOW, null),
                    new JobStats.SourceStats("Remotive", 0, remotiveLastSync, null)), remotiveLastSync);
        }

        @Override
        public boolean recalculateDemand() {
            return false;
        }
    }
}
