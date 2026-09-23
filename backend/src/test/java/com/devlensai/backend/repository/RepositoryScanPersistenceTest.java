package com.devlensai.backend.repository;

import com.devlensai.backend.entity.*;
import com.devlensai.backend.service.RepositoryJobStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RepositoryScanPersistenceTest {
    @Autowired UserRepository userRepository;
    @Autowired RepositorySnapshotRepository snapshotRepository;
    @Autowired RepositoryScanRepository scanRepository;
    @Autowired RepositoryJobRepository jobRepository;

    @Test
    void persistsScannerMetadataAndScopesLookupByOwner() {
        User owner = userRepository.save(new User("Owner", "owner@scan.test", "x".repeat(60)));
        User other = userRepository.save(new User("Other", "other@scan.test", "x".repeat(60)));
        RepositorySnapshot snapshot = snapshotRepository.save(new RepositorySnapshot(owner, "a".repeat(36),
                "sample.zip", 12, List.of(new RepositorySnapshotFile("src/A.java", "b".repeat(64), 12))));
        RepositoryScan scan = scanRepository.save(new RepositoryScan(snapshot, owner, "lexical-v1", "COMPLETED", "Java",
                List.of(new RepositoryModuleRecord("root", "", "JAVA_MAVEN", "pom.xml")),
                List.of(new RepositoryFileRecord("src/A.java", "JAVA", "b".repeat(64), 1,
                        "PARSED", "HEURISTIC", "", "class A {}")),
                List.of(new RepositorySymbolRecord("src/A.java", "A", "CLASS", 1, "HEURISTIC")),
                List.of(), List.of(), List.of()));

        assertThat(scanRepository.findBySnapshotIdAndUserId(snapshot.getId(), owner.getId()))
                .contains(scan);
        assertThat(scanRepository.findBySnapshotIdAndUserId(snapshot.getId(), other.getId()))
                .isEmpty();
    }

    @Test
    void recoversInterruptedJobsAndPersistsCancellation() {
        User owner = userRepository.save(new User("Jobs", "jobs@scan.test", "x".repeat(60)));
        RepositorySnapshot snapshot = snapshotRepository.save(new RepositorySnapshot(owner, "c".repeat(36),
                "jobs.zip", 1, List.of(new RepositorySnapshotFile("A.java", "d".repeat(64), 1))));
        RepositoryJob running = new RepositoryJob(owner, snapshot, RepositoryJobType.SCAN, "jobs.zip");
        running.running();
        running = jobRepository.save(running);
        RepositoryJob queued = jobRepository.save(new RepositoryJob(owner, snapshot, RepositoryJobType.SCAN, "jobs.zip"));
        RepositoryJobStateService state = new RepositoryJobStateService(jobRepository, snapshotRepository);

        assertThat(state.recoverInterrupted()).isEqualTo(2);
        assertThat(jobRepository.findById(running.getId()).orElseThrow().getStatus()).isEqualTo(RepositoryJobStatus.FAILED);
        RepositoryJob cancellable = jobRepository.save(new RepositoryJob(owner, snapshot, RepositoryJobType.SCAN, "jobs.zip"));
        assertThat(state.requestCancel(cancellable.getId(), owner.getId()).getStatus()).isEqualTo(RepositoryJobStatus.CANCELLED);
        assertThat(queued.getStatus()).isEqualTo(RepositoryJobStatus.FAILED);
    }
}
