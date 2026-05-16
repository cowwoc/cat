/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * Checks for CAT version upgrades on session start.
 * <p>
 * Compares the plugin version to the version recorded in {@code .cat/VERSION}.
 * On upgrade, backs up state, runs pending migrations, and updates the VERSION file.
 * On downgrade, warns the user.
 */
public final class CheckDataMigration implements SessionStartHandler
{
  private final AgentPluginScope scope;

  /**
   * Creates a new CheckDataMigration handler.
   *
   * @param scope the JVM scope providing environment configuration
   * @throws NullPointerException if scope is null
   */
  public CheckDataMigration(AgentPluginScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Checks for version changes and runs migrations if needed.
   *
   * @return a result with migration status as context, or empty if no action needed
   * @throws WrappedCheckedException if an I/O error occurs reading configuration or running migrations
   */
  @Override
  public Result handle()
  {
    try
    {
      Result workDirResult = handleWorkDirectoryMigration();
      if (!workDirResult.additionalContext().isEmpty())
        return workDirResult;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }

    Path configFile = scope.getCatDir().resolve("config.json");
    if (!Files.isRegularFile(configFile))
      return Result.empty();

    Path pluginRoot = scope.getPluginRoot();

    try
    {
      String lastMigratedVersion = getLastMigratedVersion();
      String pluginVersion = VersionUtils.getPluginVersion(scope);

      int cmp = VersionUtils.compareVersions(lastMigratedVersion, pluginVersion);

      if (cmp == 0)
        return handleCurrentInstall(pluginVersion, pluginRoot);

      if (cmp > 0)
        return handleDowngrade(lastMigratedVersion, pluginVersion);

      return handleUpgrade(lastMigratedVersion, pluginVersion, pluginRoot);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Handles work directory migration if a migration marker is present.
   *
   * @return a result with migration status, or empty if no migration needed
   * @throws IOException if reading the migration marker or performing migration fails
   */
  private Result handleWorkDirectoryMigration() throws IOException
  {
    Path migrationMarker = scope.getProjectPath().resolve(".cat/migration-pending.json");
    if (!Files.isRegularFile(migrationMarker))
      return Result.empty();

    JsonNode root = scope.getJsonMapper().readTree(Files.readString(migrationMarker));
    JsonNode oldPathNode = root.get("oldPath");
    JsonNode newPathNode = root.get("newPath");

    if (oldPathNode == null || newPathNode == null)
    {
      Files.deleteIfExists(migrationMarker);
      return Result.empty();
    }

    String oldPath = oldPathNode.asString();
    String newPath = newPathNode.asString();

    String projectDir = scope.getProjectPath().toString();
    oldPath = oldPath.replace("${CLAUDE_PROJECT_DIR}", projectDir);

    String home = System.getProperty("user.home");
    if (newPath.startsWith("~"))
      newPath = home + newPath.substring(1);

    Path oldDir = Path.of(oldPath);
    if (Files.isDirectory(oldDir))
    {
      List<String> worktrees = getWorktreesInDirectory(oldDir);
      List<Path> lockFiles = getLockFilesInDirectory(oldDir);

      if (!worktrees.isEmpty() || !lockFiles.isEmpty())
      {
        StringBuilder errorMessage = new StringBuilder(512);
        errorMessage.append("❌ Cannot migrate CAT work directory: active state detected\n\n").
          append("Old directory: ").append(oldPath).append("\n\n");

        if (!worktrees.isEmpty())
        {
          errorMessage.append("Active worktrees:\n");
          for (String worktree : worktrees)
            errorMessage.append("  - ").append(worktree).append('\n');
          errorMessage.append('\n');
        }

        if (!lockFiles.isEmpty())
        {
          errorMessage.append("Active lock files:\n");
          for (Path lockFile : lockFiles)
            errorMessage.append("  - ").append(lockFile).append('\n');
          errorMessage.append('\n');
        }

        errorMessage.append("Migration cannot proceed while work is in progress.\n").
          append("Complete or abandon work in these locations before retrying.");
        return Result.stderr(errorMessage.toString());
      }
    }

    StringBuilder message = new StringBuilder(256);
    message.append("🔄 Migrating CAT work directory...\n").
      append("   From: ").append(oldPath).append('\n').
      append("   To: ").append(newPath);

    if (Files.isDirectory(oldDir))
    {
      deleteDirectoryRecursively(oldDir);
      message.append("\n   Removed old directory");
    }

    removeFromGitignore(oldPath, projectDir);

    Files.deleteIfExists(migrationMarker);

    message.append("\n✓ Migration complete!");

    return Result.stderr(message.toString());
  }

  /**
   * Removes a path from the appropriate .gitignore file.
   *
   * @param absolutePath the absolute path to remove
   * @param projectDir the project directory path
   * @throws IOException if reading or writing the .gitignore file fails
   */
  private void removeFromGitignore(String absolutePath, String projectDir) throws IOException
  {
    String relativePath;
    Path gitignoreFile;

    if (absolutePath.startsWith(projectDir + "/.cat/"))
    {
      gitignoreFile = Path.of(projectDir, ".cat", ".gitignore");
      relativePath = absolutePath.substring((projectDir + "/.cat/").length());
    }
    else if (absolutePath.startsWith(projectDir + "/"))
    {
      gitignoreFile = Path.of(projectDir, ".gitignore");
      relativePath = absolutePath.substring((projectDir + "/").length());
    }
    else
    {
      return;
    }

    if (!Files.isRegularFile(gitignoreFile))
      return;

    List<String> lines = Files.readAllLines(gitignoreFile);
    List<String> filtered = new ArrayList<>();
    for (String line : lines)
    {
      if (!line.equals(relativePath))
        filtered.add(line);
    }

    if (filtered.size() != lines.size())
      Files.write(gitignoreFile, filtered);
  }

  /**
   * Gets the list of git worktrees that are located within the specified directory.
   *
   * @param directory the directory to check for worktrees
   * @return list of worktree paths that are under the specified directory
   * @throws IOException if running git worktree list fails
   */
  private List<String> getWorktreesInDirectory(Path directory) throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder("git", "worktree", "list", "--porcelain");
    pb.directory(scope.getProjectPath().toFile());
    Process process = pb.start();

    StringJoiner output = new StringJoiner("\n");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
      UTF_8)))
    {
      for (String line = reader.readLine(); line != null; line = reader.readLine())
        output.add(line);
    }

    try
    {
      process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for git worktree list", e);
    }

    if (process.exitValue() != 0)
      return List.of();

    List<String> worktrees = new ArrayList<>();
    String absoluteDir = directory.toAbsolutePath().toString();

    for (String line : output.toString().split("\n"))
    {
      if (line.startsWith("worktree "))
      {
        String worktreePath = line.substring("worktree ".length());
        if (worktreePath.startsWith(absoluteDir))
          worktrees.add(worktreePath);
      }
    }
    return worktrees;
  }

  /**
   * Gets the list of lock files within the specified directory.
   * <p>
   * Lock files indicate active Claude instances working on issues.
   *
   * @param directory the directory to check for lock files
   * @return list of lock file paths found in the directory
   * @throws IOException if reading the directory fails
   */
  private List<Path> getLockFilesInDirectory(Path directory) throws IOException
  {
    List<Path> lockFiles = new ArrayList<>();
    Path locksDir = directory.resolve("locks");

    if (!Files.isDirectory(locksDir))
      return lockFiles;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(locksDir, "*.lock"))
    {
      for (Path lockFile : stream)
        lockFiles.add(lockFile);
    }

    return lockFiles;
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param directory the directory to delete
   * @throws IOException if deletion fails
   */
  private void deleteDirectoryRecursively(Path directory) throws IOException
  {
    if (!Files.exists(directory))
      return;

    if (Files.isDirectory(directory))
    {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory))
      {
        for (Path entry : stream)
          deleteDirectoryRecursively(entry);
      }
    }
    Files.delete(directory);
  }

  /**
   * Reads the last migrated version from {@code .cat/VERSION}.
   *
   * @return the version string, or "0.0.0" if the file is absent or empty
   * @throws IOException if reading the VERSION file fails
   */
  private String getLastMigratedVersion() throws IOException
  {
    Path versionFile = scope.getCatDir().resolve("VERSION");
    if (!Files.isRegularFile(versionFile))
      return "0.0.0";
    String version = Files.readString(versionFile).strip();
    if (version.isEmpty())
      return "0.0.0";
    return version;
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
      ".cat/VERSION contains " + lastMigratedVersion +
      " but the plugin is version " + pluginVersion + ".\n" +
      "This appears to be a downgrade.\n" +
      "\n" +
      "**Action Required**: If this is intentional, manually update the version " +
      "in .cat/VERSION.\n" +
      "Automatic downgrade migration is not supported to prevent data loss.";
    return Result.context(message);
  }

  /**
   * Handles a detected upgrade by running pending migrations.
   *
   * @param lastMigratedVersion the previous version
   * @param pluginVersion the target version
   * @param pluginRoot the plugin root directory
   * @return a result with migration status
   * @throws IOException if reading the migration registry, creating a backup, or updating the VERSION file fails
   */
  private Result handleUpgrade(String lastMigratedVersion, String pluginVersion,
    Path pluginRoot) throws IOException
  {
    List<Migration> pendingMigrations = getPendingMigrations(lastMigratedVersion, pluginVersion,
      pluginRoot);

    if (pendingMigrations.isEmpty())
    {
      setLastMigratedVersion(pluginVersion);
      PluginCacheInstallMarker.write(pluginVersion, scope);
      return Result.context("CAT upgraded: " + lastMigratedVersion + " -> " + pluginVersion +
        " (no migrations required)");
    }

    // Create backup before migration
    String backupPath = backupCatDir("pre-upgrade-" + pluginVersion);

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
          warnings.add("CheckDataMigration: Migration script escapes migrations directory: " + scriptPath);
          migrationLog.append("\n- ").append(migration.version()).append(": SKIPPED (invalid path)");
          continue;
        }
      }
      catch (IOException _)
      {
        warnings.add("CheckDataMigration: Cannot resolve migration script path: " + scriptPath);
        migrationLog.append("\n- ").append(migration.version()).append(": SKIPPED (unresolvable path)");
        continue;
      }

      ProcessRunner.Result result = ProcessRunner.run(scope.getProjectPath(), scriptPath.toString());
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

    setLastMigratedVersion(pluginVersion);
    PluginCacheInstallMarker.write(pluginVersion, scope);

    String stderrMessage = "\n" +
      "CAT UPGRADED from version " + lastMigratedVersion + " to " + pluginVersion + "\n";
    if (!warnings.isEmpty())
      stderrMessage = stderrMessage + "Warnings:\n" + String.join("\n", warnings) + "\n";
    String contextMessage = "CAT upgraded from " + lastMigratedVersion + " to " + pluginVersion +
      ". Backup at: " + backupPath;

    return Result.both(contextMessage, stderrMessage);
  }

  /**
   * Re-runs the current-version migration once per installed plugin cache.
   * <p>
   * The project {@code VERSION} file tracks project schema state. This marker tracks whether the
   * installed plugin cache has performed install-local work such as copying custom agents. A
   * plugin uninstall removes the cache, so reinstalling the same version re-runs the idempotent
   * current-version migration without requiring a project version bump.
   *
   * @param pluginVersion the current plugin version
   * @param pluginRoot the plugin root directory
   * @return a result with migration status, or empty if no action was needed
   * @throws IOException if migration execution or marker writes fail
   */
  private Result handleCurrentInstall(String pluginVersion, Path pluginRoot) throws IOException
  {
    Path marker = PluginCacheInstallMarker.getMarker(pluginVersion, scope);
    if (marker == null || Files.isRegularFile(marker))
      return Result.empty();

    Migration migration = getMigration(pluginVersion, pluginRoot);
    if (migration == null)
    {
      PluginCacheInstallMarker.write(pluginVersion, scope);
      return Result.empty();
    }

    ProcessRunner.Result result = runMigration(migration, pluginRoot);
    if (result.exitCode() != 0)
    {
      return Result.context("CAT INSTALL MIGRATION FAILED\n" +
        "\n" +
        "Attempted current-version migration: " + pluginVersion + "\n" +
        "\n" +
        "Please review the error and try again.");
    }

    PluginCacheInstallMarker.write(pluginVersion, scope);
    return Result.both("CAT plugin install migration completed for version " + pluginVersion,
      "CAT plugin install migration completed for version " + pluginVersion + ".\n" +
        "If CAT custom agents were installed or updated, restart, resume, or clear the agent for " +
        "subagent changes to take effect.\n");
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
   * Gets the migration registered for a specific version.
   *
   * @param version the migration version
   * @param pluginRoot the plugin root directory
   * @return the migration, or {@code null} if no migration is registered
   * @throws IOException if reading the migration registry fails
   */
  private Migration getMigration(String version, Path pluginRoot) throws IOException
  {
    List<Migration> migrations = getPendingMigrations("0.0.0", version, pluginRoot);
    for (Migration migration : migrations)
    {
      if (migration.version().equals(version))
        return migration;
    }
    return null;
  }

  /**
   * Runs a migration script after validating that it remains inside the migrations directory.
   *
   * @param migration the migration to run
   * @param pluginRoot the plugin root directory
   * @return the process result
   * @throws IOException if the script cannot be resolved
   */
  private ProcessRunner.Result runMigration(Migration migration, Path pluginRoot) throws IOException
  {
    Path migrationsDir = pluginRoot.resolve("migrations");
    Path scriptPath = migrationsDir.resolve(migration.script());
    Path realPath = scriptPath.toRealPath();
    Path realMigrationsDir = migrationsDir.toRealPath();
    if (!realPath.startsWith(realMigrationsDir))
      throw new IOException("Migration script escapes migrations directory: " + scriptPath);

    return ProcessRunner.run(scope.getProjectPath(), scriptPath.toString());
  }

  /**
   * Writes the last migrated version to {@code .cat/VERSION}.
   *
   * @param newVersion the new version to set
   * @throws IOException if writing the VERSION file fails
   */
  private void setLastMigratedVersion(String newVersion) throws IOException
  {
    Path versionFile = scope.getCatDir().resolve("VERSION");
    Files.createDirectories(versionFile.getParent());
    Files.writeString(versionFile, newVersion + "\n");
  }

  /**
   * Creates a backup of the .cat directory in external CAT storage.
   *
   * @param reason the backup reason (used in directory name)
   * @return the backup directory path as a string
   * @throws IOException if creating the backup directory or copying files fails
   */
  private String backupCatDir(String reason) throws IOException
  {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path backupDir = scope.getCatWorkPath().resolve("backups/" + timestamp + "-" +
      sanitizeDirectoryName(reason));
    Path catDir = scope.getCatDir();

    Files.createDirectories(backupDir);
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(catDir))
    {
      for (Path entry : stream)
      {
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
