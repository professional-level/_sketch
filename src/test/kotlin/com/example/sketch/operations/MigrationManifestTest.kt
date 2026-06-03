package com.example.sketch.operations

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue

class MigrationManifestTest {

    @Test
    fun `migration manifest references every sql file`() {
        val sqlDir = Path.of("docs", "operations", "sql")
        val manifest = Files.readString(sqlDir.resolve("MIGRATION_MANIFEST.md"))
        val sqlFiles = Files.list(sqlDir).use { paths ->
            paths
                .filter { it.name.endsWith(".sql") }
                .map { it.name }
                .sorted()
                .toList()
        }

        assertTrue(sqlFiles.isNotEmpty())
        sqlFiles.forEach { fileName ->
            assertTrue(
                manifest.contains(fileName),
                "Migration manifest must reference $fileName",
            )
        }
    }

    @Test
    fun `kubernetes migration job applies migration manifest order`() {
        val runtimeManifest = Files.readString(
            Path.of("docs", "operations", "kubernetes", "trading-runtime.yaml"),
        )

        assertTrue(
            runtimeManifest.contains("MIGRATION_MANIFEST.md"),
            "Migration Job must read the explicit migration manifest.",
        )
        assertTrue(
            runtimeManifest.contains("grep -E '^[0-9]{8}_.+\\.sql$'"),
            "Migration Job must extract SQL files from the manifest order.",
        )
        assertTrue(
            !runtimeManifest.contains("for file in /migrations/*.sql"),
            "Migration Job must not rely on lexicographic glob order.",
        )
    }

    @Test
    fun `kubernetes migration job records applied migrations`() {
        val runtimeManifest = Files.readString(
            Path.of("docs", "operations", "kubernetes", "trading-runtime.yaml"),
        )

        assertTrue(
            runtimeManifest.contains("CREATE TABLE IF NOT EXISTS schema_migration"),
            "Migration Job must create a migration ledger table.",
        )
        assertTrue(
            runtimeManifest.contains("SELECT COUNT(*) FROM schema_migration"),
            "Migration Job must check whether each manifest entry was already applied.",
        )
        assertTrue(
            runtimeManifest.contains("skipping already applied migration"),
            "Migration Job must skip already applied manifest entries on redeploy.",
        )
        assertTrue(
            runtimeManifest.contains("INSERT INTO schema_migration"),
            "Migration Job must record successfully applied migrations.",
        )
    }
}
