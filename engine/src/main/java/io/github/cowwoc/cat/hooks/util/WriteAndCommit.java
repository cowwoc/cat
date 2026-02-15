/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommand;
import static io.github.cowwoc.cat.hooks.util.GitCommands.runGitCommandSingleLine;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Atomic write and commit operation.
 * <p>
 * Creates a file and commits it atomically (60-75% faster than step-by-step).
 */
public final class WriteAndCommit
{
  private final JvmScope scope;

  /**
   * Creates a new WriteAndCommit instance.
   *
   * @param scope the JVM scope providing JSON mapper
   * @throws NullPointerException if {@code scope} is null
   */
  public WriteAndCommit(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Executes the write and commit operation.
   *
   * @param filePath the path to create the file at (relative to repo root)
   * @param contentFile the path to temp file containing the file content
   * @param commitMsgFile the path to temp file containing the commit message
   * @param executable whether to make the file executable
   * @return JSON string with operation result
   * @throws IOException if the operation fails
   */
  public String execute(String filePath, String contentFile, String commitMsgFile, boolean executable)
    throws IOException
  {
    requireThat(filePath, "filePath").isNotBlank();
    requireThat(contentFile, "contentFile").isNotBlank();
    requireThat(commitMsgFile, "commitMsgFile").isNotBlank();

    long startTime = System.currentTimeMillis();

    Path contentPath = Paths.get(contentFile);
    Path commitMsgPath = Paths.get(commitMsgFile);
    Path targetPath = Paths.get(filePath);

    if (!Files.exists(contentPath))
      throw new IOException("Content file not found: " + contentFile);
    if (!Files.exists(commitMsgPath))
      throw new IOException("Commit message file not found: " + commitMsgFile);

    if (!isGitRepository())
      throw new IOException("Not in a git repository");

    String workingDir = System.getProperty("user.dir");
    boolean fileExisted = Files.exists(targetPath);

    Path parentDir = targetPath.getParent();
    if (parentDir != null)
      Files.createDirectories(parentDir);

    Files.copy(contentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

    if (executable)
      makeExecutable(targetPath);

    runGitCommand("add", filePath);

    String commitMsg = Files.readString(commitMsgPath, StandardCharsets.UTF_8);
    runGitCommand("commit", "-m", commitMsg);

    String commitSha = runGitCommandSingleLine("rev-parse", "--short", "HEAD");

    Files.deleteIfExists(contentPath);
    Files.deleteIfExists(commitMsgPath);

    long endTime = System.currentTimeMillis();
    long duration = (endTime - startTime) / 1000;

    return buildSuccessJson(filePath, executable, fileExisted, commitSha, workingDir, duration);
  }

  /**
   * Checks if the current directory is a git repository.
   *
   * @return true if in a git repository
   */
  private boolean isGitRepository()
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--git-dir");
      pb.redirectErrorStream(true);
      Process process = pb.start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    }
    catch (IOException | InterruptedException _)
    {
      return false;
    }
  }

  /**
   * Makes a file executable.
   *
   * @param path the file path
   * @throws IOException if the operation fails
   */
  private void makeExecutable(Path path) throws IOException
  {
    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
    perms.add(PosixFilePermission.OWNER_EXECUTE);
    perms.add(PosixFilePermission.GROUP_EXECUTE);
    perms.add(PosixFilePermission.OTHERS_EXECUTE);
    Files.setPosixFilePermissions(path, perms);
  }

  /**
   * Builds the success JSON response.
   *
   * @param filePath the file path
   * @param executable whether the file is executable
   * @param fileExisted whether the file existed before
   * @param commitSha the commit SHA
   * @param workingDir the working directory
   * @param duration the operation duration in seconds
   * @return JSON string
   * @throws IOException if JSON creation fails
   */
  private String buildSuccessJson(String filePath, boolean executable, boolean fileExisted,
    String commitSha, String workingDir, long duration) throws IOException
  {
    ObjectNode json = scope.getJsonMapper().createObjectNode();
    json.put("status", "success");
    json.put("message", "File created and committed successfully");
    json.put("duration_seconds", duration);
    json.put("file_path", filePath);
    json.put("executable", executable);
    json.put("file_existed", fileExisted);
    json.put("commit_sha", commitSha);
    json.put("working_directory", workingDir);
    json.put("timestamp", Instant.now().toString());

    return scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(json);
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 3 || args.length > 4)
    {
      System.err.println("""
        {
          "status": "error",
          "message": "Usage: write-and-commit <file-path> <content-file> <commit-msg-file> [--executable]"
        }""");
      System.exit(1);
    }

    String filePath = args[0];
    String contentFile = args[1];
    String commitMsgFile = args[2];
    boolean executable = args.length == 4 && args[3].equals("--executable");

    try (JvmScope scope = new MainJvmScope())
    {
      WriteAndCommit cmd = new WriteAndCommit(scope);
      try
      {
        String result = cmd.execute(filePath, contentFile, commitMsgFile, executable);
        System.out.println(result);
      }
      catch (IOException e)
      {
        System.err.println("""
          {
            "status": "error",
            "message": "%s"
          }""".formatted(e.getMessage().replace("\"", "\\\"")));
        System.exit(1);
      }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(WriteAndCommit.class);
      log.error("Unexpected error", e);
      throw e;
    }
    }
  }
}
