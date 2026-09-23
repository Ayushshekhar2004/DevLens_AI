package com.devlensai.backend.config;

import com.devlensai.backend.entity.User;
import com.devlensai.backend.repository.UserRepository;
import com.devlensai.backend.service.RepositoryImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.repository-import.cli.enabled", havingValue = "true")
public class RepositoryFolderImportCli implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFolderImportCli.class);
    private final RepositoryImportService importService;
    private final UserRepository userRepository;
    private final String source;
    private final String ownerEmail;

    public RepositoryFolderImportCli(
            RepositoryImportService importService,
            UserRepository userRepository,
            @Value("${app.repository-import.cli.source:}") String source,
            @Value("${app.repository-import.cli.owner-email:}") String ownerEmail
    ) {
        this.importService = importService;
        this.userRepository = userRepository;
        this.source = source;
        this.ownerEmail = ownerEmail;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (source.isBlank() || ownerEmail.isBlank()) {
            throw new IllegalStateException("Folder import CLI requires source and owner email configuration");
        }
        User owner = userRepository.findByEmailIgnoreCase(ownerEmail)
                .orElseThrow(() -> new IllegalStateException("Folder import owner does not exist"));
        var snapshot = importService.importFolder(owner, Path.of(source));
        LOGGER.info("Trusted folder snapshot completed: id={}, files={}", snapshot.id(), snapshot.fileCount());
    }
}
