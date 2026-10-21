package com.skillmap.api.infrastructure.scheduling;

import com.skillmap.api.application.service.JobService;
import com.skillmap.api.domain.exception.JobSourceUnavailableException;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JobSyncSchedulerTest {

    @Test
    void syncsInBackgroundOnFirstStartWhenThereAreNoPostings() {
        var jobs = new FakeJobService(0);
        List<Runnable> scheduled = new ArrayList<>();
        var scheduler = new JobSyncScheduler(jobs, true, scheduled::add);

        scheduler.syncOnFirstStart();

        assertThat(jobs.syncCalls).as("no bloquea el arranque").isZero();
        assertThat(scheduled).hasSize(1);
        scheduled.get(0).run();
        assertThat(jobs.syncCalls).isEqualTo(1);
    }

    @Test
    void doesNotSyncOnStartWhenPostingsAlreadyExist() {
        var jobs = new FakeJobService(5);

        new JobSyncScheduler(jobs, true, Runnable::run).syncOnFirstStart();

        assertThat(jobs.syncCalls).isZero();
    }

    @Test
    void doesNotSyncOnStartWhenDisabled() {
        var jobs = new FakeJobService(0);

        new JobSyncScheduler(jobs, false, Runnable::run).syncOnFirstStart();

        assertThat(jobs.syncCalls).isZero();
    }

    @Test
    void failedFirstSyncDoesNotPropagate() {
        var jobs = new FakeJobService(0);
        jobs.failure = new JobSourceUnavailableException("Remotive no responde");

        assertThatCode(() -> new JobSyncScheduler(jobs, true, Runnable::run).syncOnFirstStart())
                .doesNotThrowAnyException();
        assertThat(jobs.syncCalls).isEqualTo(1);
    }

    private static final class FakeJobService implements JobService {
        private final long postings;
        private int syncCalls;
        private RuntimeException failure;

        FakeJobService(long postings) {
            this.postings = postings;
        }

        @Override
        public JobSyncReport sync() {
            syncCalls++;
            if (failure != null) {
                throw failure;
            }
            return new JobSyncReport(List.of(), (int) postings, false);
        }

        @Override
        public JobSyncReport syncSource(String source) {
            throw new UnsupportedOperationException();
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
            return new JobStats(postings, List.of(), null);
        }

        @Override
        public boolean recalculateDemand() {
            return false;
        }
    }
}
