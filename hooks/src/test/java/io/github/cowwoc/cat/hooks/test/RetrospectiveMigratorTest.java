package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.github.cowwoc.cat.hooks.util.RetrospectiveMigrator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Tests for RetrospectiveMigrator.
 */
public final class RetrospectiveMigratorTest
{
  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param dir directory to delete
   */
  private void deleteRecursively(Path dir)
  {
    try
    {
      if (Files.exists(dir))
      {
        Files.walk(dir).
          sorted((a, b) -> -a.compareTo(b)).
          forEach(path ->
          {
            try
            {
              Files.deleteIfExists(path);
            }
            catch (Exception _)
            {
            }
          });
      }
    }
    catch (Exception _)
    {
    }
  }

  /**
   * Verifies that migration returns skipped status when directory doesn't exist.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsWhenDirectoryNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    try
    {
      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, false);

      requireThat(result.path("status").asString(), "status").isEqualTo("skipped");
      requireThat(result.path("reason").asString(), "reason").isEqualTo("directory not found");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that migration returns skipped status when already migrated.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void skipsWhenAlreadyMigrated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path indexFile = retroDir.resolve("index.json");

    try
    {
      Files.writeString(indexFile, "{}");

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, false);

      requireThat(result.path("status").asString(), "status").isEqualTo("skipped");
      requireThat(result.path("reason").asString(), "reason").isEqualTo("already migrated");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that dry run mode shows migration without creating files.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void dryRunShowsStatsWithoutCreatingFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path mistakesFile = retroDir.resolve("mistakes.json");

    try
    {
      String mistakesJson = """
        {
          "mistakes": [
            {"id": "M1", "timestamp": "2025-01-15T10:00:00Z", "description": "First mistake"},
            {"id": "M2", "timestamp": "2025-02-10T11:00:00Z", "description": "Second mistake"}
          ]
        }
        """;
      Files.writeString(mistakesFile, mistakesJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, true);

      requireThat(result.path("mistakes_total").asInt(), "mistakes_total").isEqualTo(2);
      requireThat(result.path("mistakes_by_period").path("2025-01").asInt(), "jan_mistakes").
        isEqualTo(1);
      requireThat(result.path("mistakes_by_period").path("2025-02").asInt(), "feb_mistakes").
        isEqualTo(1);

      requireThat(Files.exists(retroDir.resolve("mistakes-2025-01.json")), "jan_file_created").
        isFalse();
      requireThat(Files.exists(retroDir.resolve("mistakes-2025-02.json")), "feb_file_created").
        isFalse();
      requireThat(Files.exists(retroDir.resolve("index.json")), "index_file_created").isFalse();
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that mistakes are split by month correctly.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void splitsMistakesByMonth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path mistakesFile = retroDir.resolve("mistakes.json");

    try
    {
      String mistakesJson = """
        {
          "mistakes": [
            {"id": "M1", "timestamp": "2025-01-15T10:00:00Z", "description": "First mistake"},
            {"id": "M2", "timestamp": "2025-01-20T11:00:00Z", "description": "Second mistake"},
            {"id": "M3", "timestamp": "2025-02-10T12:00:00Z", "description": "Third mistake"}
          ]
        }
        """;
      Files.writeString(mistakesFile, mistakesJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, false);

      requireThat(result.path("status").asString(), "status").isEqualTo("success");
      requireThat(result.path("mistakes_total").asInt(), "mistakes_total").isEqualTo(3);

      Path jan2025File = retroDir.resolve("mistakes-2025-01.json");
      Path feb2025File = retroDir.resolve("mistakes-2025-02.json");

      requireThat(Files.exists(jan2025File), "jan_file_exists").isTrue();
      requireThat(Files.exists(feb2025File), "feb_file_exists").isTrue();

      JsonNode janData = mapper.readTree(Files.readString(jan2025File));
      requireThat(janData.path("period").asString(), "jan_period").isEqualTo("2025-01");
      requireThat(janData.path("mistakes").size(), "jan_mistakes_count").isEqualTo(2);
      requireThat(janData.path("mistakes").get(0).path("id").asString(), "first_mistake_id").
        isEqualTo("M1");
      requireThat(janData.path("mistakes").get(1).path("id").asString(), "second_mistake_id").
        isEqualTo("M2");

      JsonNode febData = mapper.readTree(Files.readString(feb2025File));
      requireThat(febData.path("period").asString(), "feb_period").isEqualTo("2025-02");
      requireThat(febData.path("mistakes").size(), "feb_mistakes_count").isEqualTo(1);
      requireThat(febData.path("mistakes").get(0).path("id").asString(), "third_mistake_id").
        isEqualTo("M3");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that retrospectives are split by month correctly.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void splitsRetrospectivesByMonth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path retroFile = retroDir.resolve("retrospectives.json");

    try
    {
      String retroJson = """
        {
          "last_retrospective": "2025-02-01T00:00:00Z",
          "mistake_count_since_last": 2,
          "config": {
            "mistake_count_threshold": 5,
            "trigger_interval_days": 7
          },
          "retrospectives": [
            {"id": "R1", "timestamp": "2025-01-15T10:00:00Z", "summary": "First retro"},
            {"id": "R2", "timestamp": "2025-02-10T11:00:00Z", "summary": "Second retro"}
          ]
        }
        """;
      Files.writeString(retroFile, retroJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, false);

      requireThat(result.path("status").asString(), "status").isEqualTo("success");
      requireThat(result.path("retrospectives_total").asInt(), "retrospectives_total").isEqualTo(2);

      Path jan2025File = retroDir.resolve("retrospectives-2025-01.json");
      Path feb2025File = retroDir.resolve("retrospectives-2025-02.json");

      requireThat(Files.exists(jan2025File), "jan_file_exists").isTrue();
      requireThat(Files.exists(feb2025File), "feb_file_exists").isTrue();

      JsonNode janData = mapper.readTree(Files.readString(jan2025File));
      requireThat(janData.path("period").asString(), "jan_period").isEqualTo("2025-01");
      requireThat(janData.path("retrospectives").size(), "jan_retros_count").isEqualTo(1);

      JsonNode febData = mapper.readTree(Files.readString(feb2025File));
      requireThat(febData.path("period").asString(), "feb_period").isEqualTo("2025-02");
      requireThat(febData.path("retrospectives").size(), "feb_retros_count").isEqualTo(1);
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that index.json is created with correct structure.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void createsIndexFileWithCorrectStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path mistakesFile = retroDir.resolve("mistakes.json");
    Path retroFile = retroDir.resolve("retrospectives.json");

    try
    {
      String mistakesJson = """
        {
          "mistakes": [
            {"id": "M1", "timestamp": "2025-01-15T10:00:00Z"}
          ]
        }
        """;
      Files.writeString(mistakesFile, mistakesJson);

      String retroJson = """
        {
          "last_retrospective": "2025-01-20T00:00:00Z",
          "mistake_count_since_last": 3,
          "config": {
            "mistake_count_threshold": 10,
            "trigger_interval_days": 14
          },
          "retrospectives": [
            {"id": "R1", "timestamp": "2025-01-15T10:00:00Z"}
          ]
        }
        """;
      Files.writeString(retroFile, retroJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      migrator.migrate(tempDir, false);

      Path indexFile = retroDir.resolve("index.json");
      requireThat(Files.exists(indexFile), "index_file_exists").isTrue();

      JsonNode index = mapper.readTree(Files.readString(indexFile));
      requireThat(index.path("version").asString(), "version").isEqualTo("2.0");
      requireThat(index.path("config").path("mistake_count_threshold").asInt(), "threshold").
        isEqualTo(10);
      requireThat(index.path("config").path("trigger_interval_days").asInt(), "trigger_days").
        isEqualTo(14);
      requireThat(index.path("last_retrospective").asString(), "last_retrospective").
        isEqualTo("2025-01-20T00:00:00Z");
      requireThat(index.path("mistake_count_since_last").asInt(), "mistake_count_since_last").
        isEqualTo(3);

      requireThat(index.path("files").path("mistakes").size(), "mistakes_files").isEqualTo(1);
      requireThat(index.path("files").path("mistakes").get(0).asString(), "mistakes_file").
        isEqualTo("mistakes-2025-01.json");

      requireThat(index.path("files").path("retrospectives").size(), "retros_files").isEqualTo(1);
      requireThat(index.path("files").path("retrospectives").get(0).asString(), "retros_file").
        isEqualTo("retrospectives-2025-01.json");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that original files are backed up after migration.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void backsUpOriginalFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path mistakesFile = retroDir.resolve("mistakes.json");
    Path retroFile = retroDir.resolve("retrospectives.json");

    try
    {
      String mistakesJson = """
        {
          "mistakes": [
            {"id": "M1", "timestamp": "2025-01-15T10:00:00Z"}
          ]
        }
        """;
      Files.writeString(mistakesFile, mistakesJson);

      String retroJson = """
        {
          "last_retrospective": null,
          "mistake_count_since_last": 0,
          "config": {
            "mistake_count_threshold": 5,
            "trigger_interval_days": 7
          },
          "retrospectives": []
        }
        """;
      Files.writeString(retroFile, retroJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      migrator.migrate(tempDir, false);

      requireThat(Files.exists(mistakesFile), "original_mistakes_exists").isFalse();
      requireThat(Files.exists(retroFile), "original_retro_exists").isFalse();

      Path mistakesBackup = retroDir.resolve("mistakes.json.backup");
      Path retroBackup = retroDir.resolve("retrospectives.json.backup");

      requireThat(Files.exists(mistakesBackup), "mistakes_backup_exists").isTrue();
      requireThat(Files.exists(retroBackup), "retro_backup_exists").isTrue();

      JsonNode mistakesBackupData = mapper.readTree(Files.readString(mistakesBackup));
      requireThat(mistakesBackupData.path("mistakes").size(), "backed_up_mistakes_count").
        isEqualTo(1);
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that migration succeeds when directory is empty.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void migratesEmptyDirectorySuccessfully() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);

    try
    {
      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, false);

      requireThat(result.path("status").asString(), "status").isEqualTo("success");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that different timestamp formats are correctly parsed.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void handlesVariousTimestampFormats() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path mistakesFile = retroDir.resolve("mistakes.json");

    try
    {
      String mistakesJson = """
        {
          "mistakes": [
            {"id": "M1", "timestamp": "2025-01-15T10:00:00Z"},
            {"id": "M2", "timestamp": "2025-01-20T11:00:00+00:00"},
            {"id": "M3", "timestamp": "2025-02-10T12:00:00.123Z"},
            {"id": "M4", "timestamp": "2025-02-15T13:00:00.456+00:00"}
          ]
        }
        """;
      Files.writeString(mistakesFile, mistakesJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      JsonNode result = migrator.migrate(tempDir, false);

      requireThat(result.path("status").asString(), "status").isEqualTo("success");
      requireThat(result.path("mistakes_by_period").path("2025-01").asInt(), "jan_count").
        isEqualTo(2);
      requireThat(result.path("mistakes_by_period").path("2025-02").asInt(), "feb_count").
        isEqualTo(2);

      Path jan2025File = retroDir.resolve("mistakes-2025-01.json");
      JsonNode janData = mapper.readTree(Files.readString(jan2025File));
      requireThat(janData.path("mistakes").size(), "jan_mistakes").isEqualTo(2);
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that mistakes within a period are sorted by timestamp.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void sortsMistakesWithinPeriodByTimestamp() throws IOException
  {
    Path tempDir = Files.createTempDirectory("retro-test-");
    Path claudeDir = tempDir.resolve(".claude");
    Path catDir = claudeDir.resolve("cat");
    Path retroDir = catDir.resolve("retrospectives");
    Files.createDirectories(retroDir);
    Path mistakesFile = retroDir.resolve("mistakes.json");

    try
    {
      String mistakesJson = """
        {
          "mistakes": [
            {"id": "M3", "timestamp": "2025-01-30T10:00:00Z"},
            {"id": "M1", "timestamp": "2025-01-10T10:00:00Z"},
            {"id": "M2", "timestamp": "2025-01-20T10:00:00Z"}
          ]
        }
        """;
      Files.writeString(mistakesFile, mistakesJson);

      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      migrator.migrate(tempDir, false);

      Path jan2025File = retroDir.resolve("mistakes-2025-01.json");
      JsonNode janData = mapper.readTree(Files.readString(jan2025File));

      requireThat(janData.path("mistakes").get(0).path("id").asString(), "first_id").isEqualTo("M1");
      requireThat(janData.path("mistakes").get(1).path("id").asString(), "second_id").isEqualTo("M2");
      requireThat(janData.path("mistakes").get(2).path("id").asString(), "third_id").isEqualTo("M3");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }
}
