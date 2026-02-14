package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import io.github.cowwoc.cat.hooks.util.VersionUtils;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Checks for CAT version upgrades on session start.
 * <p>
 * Compares the plugin version to the {@code last_migrated_version} in
 * {@code cat-config.json}. On upgrade, backs up state, runs pending migrations, and
 * updates the config. On downgrade, warns the user.
 */
public final class CheckUpgrade implements SessionStartHandler
{
  private final JvmScope scope;

  /**
   * Creates a new CheckUpgrade handler.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if scope is null
   */
  public CheckUpgrade(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Checks for version changes and runs migrations if needed.
   *
   * @param input the hook input
   * @return a result with migration status as context, or empty if no action needed
   * @throws NullPointerException if input is null
   * @throws WrappedCheckedException if an I/O error occurs reading configuration or running migrations
   */
  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();
    Path projectDir = scope.getClaudeProjectDir();

    Path configFile = projectDir.resolve(".claude/cat/cat-config.json");
    if (!Files.isRegularFile(configFile))
      return Result.empty();

    Path pluginRoot = scope.getClaudePluginRoot();

    try
    {
      String lastMigratedVersion = getLastMigratedVersion(configFile);
      String pluginVersion = VersionUtils.getPluginVersion(scope.getJsonMapper(), pluginRoot);

      int cmp = VersionUtils.compareVersions(lastMigratedVersion, pluginVersion);

      if (cmp == 0)
        return Result.empty();

      if (cmp > 0)
        return handleDowngrade(lastMigratedVersion, pluginVersion);

      return handleUpgrade(lastMigratedVersion, pluginVersion, configFile, pluginRoot, projectDir);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Reads the last migrated version from cat-config.json.
   *
   * @param configFile the config file path
   * @return the version string, or "0.0.0" if the field is absent or invalid
   * @throws IOException if reading the config file fails
   */
  private String getLastMigratedVersion(Path configFile) throws IOException
  {
    JsonNode root = scope.getJsonMapper().readTree(Files.readString(configFile));
    JsonNode versionNode = root.get("last_migrated_version");
    if (versionNode != null && versionNode.isString())
    {
      String version = versionNode.asString();
      if (!version.isEmpty() && !version.equals("null"))
        return version;
    }
    return "0.0.0";
  }

  /**
   * Handles a detected downgrade by returning a warning.
   *
   * @param lastMigratedVersion the config version
   * @param pluginVersion the plugin version
   * @return a result with the downgrade warning
   */
  private Result handleDowngrade(String lastMigratedVersion, String pluginVersion)
  {
    String message = "CAT VERSION MISMATCH DETECTED\n" +
      "\n" +
      "Your config has last_migrated_version " + lastMigratedVersion +
      " but the plugin is version " + pluginVersion + ".\n" +
      "This appears to be a downgrade.\n" +
      "\n" +
      "**Action Required**: If this is intentional, manually update last_migrated_version " +
      "in .claude/cat/cat-config.json.\n" +
      "Automatic downgrade migration is not supported to prevent data loss.";
    return Result.context(message);
  }

  /**
   * Handles a detected upgrade by running pending migrations.
   *
   * @param lastMigratedVersion the previous version
   * @param pluginVersion the target version
   * @param configFile the config file path
   * @param pluginRoot the plugin root directory
   * @param projectDir the project directory
   * @return a result with migration status
   * @throws IOException if reading the migration registry, creating a backup, or updating the config fails
   */
  private Result handleUpgrade(String lastMigratedVersion, String pluginVersion,
    Path configFile, Path pluginRoot, Path projectDir) throws IOException
  {
    List<Migration> pendingMigrations = getPendingMigrations(lastMigratedVersion, pluginVersion,
      pluginRoot);

    if (pendingMigrations.isEmpty())
    {
      setLastMigratedVersion(configFile, pluginVersion);
      return Result.context("CAT upgraded: " + lastMigratedVersion + " -> " + pluginVersion +
        " (no migrations required)");
    }

    // Create backup before migration
    String backupPath = backupCatDir(projectDir, "pre-upgrade-" + pluginVersion);

    // Run migrations
    StringBuilder migrationLog = new StringBuilder(128);
    List<String> warnings = new ArrayList<>();
    boolean failed = false;

    Path migrationsDir = pluginRoot.resolve("migrations");
    for (Migration migration : pendingMigrations)
    {
      Path scriptPath = migrationsDir.resolve(migration.script());
      try
      {
        Path realPath = scriptPath.toRealPath();
        Path realMigrationsDir = migrationsDir.toRealPath();
        if (!realPath.startsWith(realMigrationsDir))
        {
          warnings.add("CheckUpgrade: Migration script escapes migrations directory: " + scriptPath);
          migrationLog.append("\n- ").append(migration.version()).append(": SKIPPED (invalid path)");
          continue;
        }
      }
      catch (IOException _)
      {
        warnings.add("CheckUpgrade: Cannot resolve migration script path: " + scriptPath);
        migrationLog.append("\n- ").append(migration.version()).append(": SKIPPED (unresolvable path)");
        continue;
      }

      ProcessRunner.Result result = ProcessRunner.run(scriptPath.toString());
      if (result.exitCode() == 0)
      {
        migrationLog.append("\n- ").append(migration.version()).append(": success");
      }
      else
      {
        migrationLog.append("\n- ").append(migration.version()).append(": FAILED");
        failed = true;
        break;
      }
    }

    if (failed)
    {
      String message = "CAT UPGRADE FAILED\n" +
        "\n" +
        "Attempted upgrade: " + lastMigratedVersion + " -> " + pluginVersion + "\n" +
        "\n" +
        "Migration log:" + migrationLog + "\n" +
        "\n" +
        "**Backup preserved at**: " + backupPath + "\n" +
        "\n" +
        "Please review the error and try again, or restore from backup.";
      if (!warnings.isEmpty())
        message = message + "\n\nWarnings:\n" + String.join("\n", warnings);
      return Result.context(message);
    }

    setLastMigratedVersion(configFile, pluginVersion);

    String stderrMessage = "\n" +
      "CAT UPGRADED from version " + lastMigratedVersion + " to " + pluginVersion + "\n";
    if (!warnings.isEmpty())
      stderrMessage = stderrMessage + "Warnings:\n" + String.join("\n", warnings) + "\n";
    String contextMessage = "CAT upgraded from " + lastMigratedVersion + " to " + pluginVersion +
      ". Backup at: " + backupPath;

    return Result.both(contextMessage, stderrMessage);
  }

  /**
   * Gets the list of pending migrations between two versions.
   *
   * @param fromVersion the starting version (exclusive)
   * @param toVersion the target version (inclusive)
   * @param pluginRoot the plugin root directory
   * @return list of migrations to run, sorted by version
   * @throws IOException if reading the migration registry fails
   */
  private List<Migration> getPendingMigrations(String fromVersion, String toVersion,
    Path pluginRoot) throws IOException
  {
    Path registryFile = pluginRoot.resolve("migrations/registry.json");
    if (!Files.isRegularFile(registryFile))
      return List.of();

    JsonNode root = scope.getJsonMapper().readTree(Files.readString(registryFile));
    JsonNode migrations = root.get("migrations");
    if (migrations == null || !migrations.isArray())
      return List.of();

    List<Migration> pending = new ArrayList<>();
    for (JsonNode entry : migrations)
    {
      JsonNode versionNode = entry.get("version");
      JsonNode scriptNode = entry.get("script");
      if (versionNode == null || scriptNode == null)
        continue;
      String version = versionNode.asString();
      String script = scriptNode.asString();
      if (version.isEmpty() || script.isEmpty())
        continue;

      // Include if version > fromVersion AND version <= toVersion
      if (VersionUtils.compareVersions(version, fromVersion) > 0 &&
        VersionUtils.compareVersions(version, toVersion) <= 0)
        pending.add(new Migration(version, script));
    }
    pending.sort(Comparator.comparing(Migration::version, VersionUtils::compareVersions));
    return pending;
  }

  /**
   * Updates the last_migrated_version in cat-config.json.
   *
   * @param configFile the config file path
   * @param newVersion the new version to set
   * @throws IOException if reading or writing the config file fails
   */
  private void setLastMigratedVersion(Path configFile, String newVersion) throws IOException
  {
    JsonNode root = scope.getJsonMapper().readTree(Files.readString(configFile));
    if (root instanceof ObjectNode objectNode)
      objectNode.put("last_migrated_version", newVersion);
    Files.writeString(configFile, scope.getJsonMapper().writeValueAsString(root));
  }

  /**
   * Creates a backup of the .claude/cat directory.
   *
   * @param projectDir the project root directory
   * @param reason the backup reason (used in directory name)
   * @return the backup directory path as a string
   * @throws IOException if creating the backup directory or copying files fails
   */
  private String backupCatDir(Path projectDir, String reason) throws IOException
  {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path backupDir = projectDir.resolve(".claude/cat/backups/" + timestamp + "-" +
      sanitizeDirectoryName(reason));
    Path catDir = projectDir.resolve(".claude/cat");

    Files.createDirectories(backupDir);
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(catDir))
    {
      for (Path entry : stream)
      {
        if (entry.getFileName().toString().equals("backups"))
          continue;
        Path target = backupDir.resolve(entry.getFileName());
        copyRecursively(entry, target);
      }
    }
    return backupDir.toString();
  }

  /**
   * Sanitizes a string for use as a directory name by replacing path separators and special
   * characters with underscores.
   *
   * @param name the name to sanitize
   * @return the sanitized name
   */
  private String sanitizeDirectoryName(String name)
  {
    String sanitized = name.replaceAll("[/\\\\:*?\"<>|.]", "_");
    if (sanitized.isEmpty())
      return "backup";
    return sanitized;
  }

  /**
   * Recursively copies a file or directory.
   * <p>
   * Skips symbolic links and enforces a maximum directory nesting depth of 100
   * to prevent unbounded recursion.
   *
   * @param source the source path
   * @param target the target path
   * @throws IOException if copying fails or depth limit is exceeded
   */
  private void copyRecursively(Path source, Path target) throws IOException
  {
    copyRecursively(source, target, 0);
  }

  /**
   * Recursively copies a file or directory with depth tracking.
   *
   * @param source the source path
   * @param target the target path
   * @param depth the current nesting depth
   * @throws IOException if copying fails or depth limit is exceeded
   */
  private void copyRecursively(Path source, Path target, int depth) throws IOException
  {
    if (depth > 100)
      throw new IOException("Directory nesting exceeds maximum depth of 100");
    if (Files.isSymbolicLink(source))
      return;
    if (Files.isDirectory(source))
    {
      Files.createDirectories(target);
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(source))
      {
        for (Path entry : stream)
          copyRecursively(entry, target.resolve(entry.getFileName()), depth + 1);
      }
    }
    else
    {
      Files.copy(source, target);
    }
  }

  /**
   * A migration entry from registry.json.
   *
   * @param version the target version for this migration
   * @param script the script filename to execute
   */
  private record Migration(String version, String script)
  {
    /**
     * Creates a new migration entry.
     *
     * @param version the target version for this migration
     * @param script the script filename to execute
     * @throws NullPointerException if any parameter is null
     */
    private Migration
    {
      requireThat(version, "version").isNotNull();
      requireThat(script, "script").isNotNull();
    }
  }
}
