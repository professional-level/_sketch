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
}
