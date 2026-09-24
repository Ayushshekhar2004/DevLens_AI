package com.devlensai.backend.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryScanMigrationTest {
    @Test
    void appliesAdditiveScannerMigrationToExistingSchema() throws Exception {
        String url = "jdbc:h2:mem:scanmigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE repository_snapshots (id BIGINT PRIMARY KEY)");
        }

        var result = Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion("0").locations("classpath:db/migration")
                .load().migrate();

        assertThat(result.migrationsExecuted).isEqualTo(4);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var metadata = connection.getMetaData();
            for (String table : new String[]{"repository_scans", "repository_scan_modules", "repository_scan_files",
                    "repository_scan_symbols", "repository_scan_imports", "repository_scan_dependency_edges",
                    "repository_scan_skips"}) {
                try (var tables = metadata.getTables(null, null, table, new String[]{"TABLE"})) {
                    assertThat(tables.next()).as(table).isTrue();
                }
            }
            try (var tables = metadata.getTables(null, null, "repository_jobs", new String[]{"TABLE"})) {
                assertThat(tables.next()).as("repository_jobs").isTrue();
            }
            for (String table : new String[]{"repository_analysis_jobs", "repository_analysis_stages", "repository_analysis_units",
                    "repository_summaries", "repository_summary_evidence"}) {
                try (var tables = metadata.getTables(null, null, table, new String[]{"TABLE"})) {
                    assertThat(tables.next()).as(table).isTrue();
                }
            }
        }
    }
}
