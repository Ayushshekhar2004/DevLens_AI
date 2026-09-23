package com.devlensai.backend.service;

import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryJobRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class RepositoryJobStateService {
    private final RepositoryJobRepository jobs;
    private final RepositorySnapshotRepository snapshots;

    public RepositoryJobStateService(RepositoryJobRepository jobs, RepositorySnapshotRepository snapshots) {
        this.jobs = jobs;
        this.snapshots = snapshots;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryJob create(User user, RepositorySnapshot snapshot, RepositoryJobType type, String sourceName) {
        return jobs.save(new RepositoryJob(user, snapshot, type, sourceName));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryJob markRunning(Long id) { RepositoryJob job = required(id); job.running(); return job; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryJob markCompleted(Long id, Long snapshotId) {
        RepositoryJob job = required(id);
        RepositorySnapshot snapshot = snapshotId == null ? job.getSnapshot() : snapshots.findById(snapshotId)
                .orElseThrow(() -> new RepositorySnapshotNotFoundException(snapshotId));
        job.completed(snapshot);
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryJob markFailed(Long id, String message) { RepositoryJob job = required(id); job.failed(message); return job; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryJob requestCancel(Long id, Long userId) {
        RepositoryJob job = jobs.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
        if (EnumSet.of(RepositoryJobStatus.QUEUED, RepositoryJobStatus.RUNNING).contains(job.getStatus())) job.requestCancellation();
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryJob markCancelled(Long id) { RepositoryJob job = required(id); job.cancelled(); return job; }

    @Transactional(readOnly = true)
    public RepositoryJob owned(Long id, Long userId) {
        return jobs.findByIdAndUserId(id, userId).orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<RepositoryJob> activeOrCompletedScan(Long snapshotId) {
        return jobs.findFirstBySnapshotIdAndTypeAndStatusInOrderByCreatedAtDesc(snapshotId, RepositoryJobType.SCAN,
                List.of(RepositoryJobStatus.QUEUED, RepositoryJobStatus.RUNNING, RepositoryJobStatus.COMPLETED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverInterrupted() {
        List<RepositoryJob> interrupted = jobs.findByStatusIn(List.of(RepositoryJobStatus.QUEUED, RepositoryJobStatus.RUNNING));
        interrupted.forEach(job -> job.failed("Operation was interrupted by an application restart; retry safely"));
        return interrupted.size();
    }

    @Transactional(readOnly = true)
    public boolean cancellationRequested(Long id) { return required(id).isCancelRequested(); }

    private RepositoryJob required(Long id) {
        return jobs.findById(id).orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
    }
}
