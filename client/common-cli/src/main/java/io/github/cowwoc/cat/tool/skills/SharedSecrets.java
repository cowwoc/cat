/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.tool.util.IssueDiscovery;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * A repository of "shared secrets" for calling package-private constructors from other modules without
 * using reflection.
 * <p>
 * Each class that exposes package-private functionality registers an access implementation via a static
 * initializer. Consumers retrieve the access object and invoke methods on it.
 *
 * @see <a href="https://stackoverflow.com/questions/46722452/how-does-the-sharedsecrets-mechanism-work">
 *   How does the SharedSecrets mechanism work?</a>
 */
public final class SharedSecrets
{
  private static final Lookup LOOKUP = MethodHandles.lookup();
  private static EmpiricalTestRunnerAccess empiricalTestRunnerAccess;
  private static InstructionTestRunnerAccess instructionTestRunnerAccess;
  private static StatuslineCommandAccess statuslineCommandAccess;

  private SharedSecrets()
  {
  }

  /**
   * Parses the status field from index.json content and validates it against canonical values.
   *
   * @param content the JSON content of the index.json file
   * @param indexPath the path to the index.json file (used in error messages only)
   * @param mapper the JSON mapper to use for parsing
   * @return the validated status string, or {@code "open"} if the status field is absent
   * @throws NullPointerException if {@code content}, {@code indexPath}, or {@code mapper} are null
   * @throws IOException if the status value is present but non-canonical
   */
  public static String getIssueStatus(String content, Path indexPath, JsonMapper mapper) throws IOException
  {
    requireThat(content, "content").isNotNull();
    requireThat(indexPath, "indexPath").isNotNull();
    requireThat(mapper, "mapper").isNotNull();
    return IssueDiscovery.parseIssueStatus(content, indexPath, mapper);
  }

  /**
   * Registers the access object for {@link EmpiricalTestRunner}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setEmpiricalTestRunnerAccess(EmpiricalTestRunnerAccess access)
  {
    requireThat(access, "access").isNotNull();
    empiricalTestRunnerAccess = access;
  }

  /**
   * Creates an isolated git worktree for a single empirical test run.
   *
   * @param baseRepo the base git repository to branch from
   * @return the path of the newly created worktree
   * @throws NullPointerException if {@code baseRepo} is null
   * @throws IOException if the temporary directory cannot be created or the git command fails
   */
  public static Path createTestWorktree(Path baseRepo) throws IOException
  {
    requireThat(baseRepo, "baseRepo").isNotNull();
    if (empiricalTestRunnerAccess == null)
      initialize(EmpiricalTestRunner.class);
    return empiricalTestRunnerAccess.createTestWorktree(baseRepo);
  }

  /**
   * Removes a test worktree created by {@link #createTestWorktree(Path)}.
   *
   * @param baseRepo     the base git repository
   * @param worktreePath the worktree path to remove
   * @throws NullPointerException if {@code baseRepo} or {@code worktreePath} are null
   */
  public static void removeTestWorktree(Path baseRepo, Path worktreePath)
  {
    requireThat(baseRepo, "baseRepo").isNotNull();
    requireThat(worktreePath, "worktreePath").isNotNull();
    if (empiricalTestRunnerAccess == null)
      initialize(EmpiricalTestRunner.class);
    empiricalTestRunnerAccess.removeTestWorktree(baseRepo, worktreePath);
  }

  /**
   * Registers the access object for {@link InstructionTestRunner}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setInstructionTestRunnerAccess(InstructionTestRunnerAccess access)
  {
    requireThat(access, "access").isNotNull();
    instructionTestRunnerAccess = access;
  }

  /**
   * Computes the SHA-256 hex digest of the given bytes.
   *
   * @param bytes the bytes to hash
   * @return lowercase hex SHA-256 digest
   * @throws NullPointerException if {@code bytes} is null
   */
  public static String sha256Bytes(byte[] bytes)
  {
    requireThat(bytes, "bytes").isNotNull();
    if (instructionTestRunnerAccess == null)
      initialize(InstructionTestRunner.class);
    return instructionTestRunnerAccess.sha256Bytes(bytes);
  }

  /**
   * Builds the grader argument array for ClaudeRunner invocation.
   * <p>
   * Exposed for testing to validate the --agent argument is correctly constructed.
   *
   * @param graderPromptFile the grader prompt file path
   * @param modelId the model ID to use
   * @param runnerWorktree the runner worktree path
   * @param jlinkBin the jlink binary path
   * @return the grader arguments array
   * @throws NullPointerException if any parameter is null
   */
  public static String[] buildGraderArgs(Path graderPromptFile, String modelId, String runnerWorktree,
    Path jlinkBin)
  {
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotNull();
    requireThat(runnerWorktree, "runnerWorktree").isNotNull();
    requireThat(jlinkBin, "jlinkBin").isNotNull();
    if (instructionTestRunnerAccess == null)
      initialize(InstructionTestRunner.class);
    return instructionTestRunnerAccess.buildGraderArgs(graderPromptFile, modelId, runnerWorktree, jlinkBin);
  }

  /**
   * Registers the access object for the Claude statusline command.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setStatuslineCommandAccess(StatuslineCommandAccess access)
  {
    requireThat(access, "access").isNotNull();
    statuslineCommandAccess = access;
  }

  /**
   * Scales the number of used tokens against the usable context window.
   * <p>
   * The usable context window is the total context minus a fixed overhead. Tokens at or below overhead
   * map to 0%; tokens at the total context ceiling map to 100%.
   *
   * @param usedTokens   the number of tokens used in the context window
   * @param totalContext the total context window size in tokens
   * @return the scaled percentage in the range [0, 100]
   */
  public static int scaleContextPercent(int usedTokens, int totalContext)
  {
    if (statuslineCommandAccess == null)
      initialize("io.github.cowwoc.cat.claude.hook.util.StatuslineCommand");
    return statuslineCommandAccess.scaleContextPercent(usedTokens, totalContext);
  }

  /**
   * Initializes a class. If the class is already initialized, this method has no effect.
   *
   * @param clazz the class
   */
  private static void initialize(Class<?> clazz)
  {
    try
    {
      LOOKUP.ensureInitialized(clazz);
    }
    catch (IllegalAccessException e)
    {
      throw new AssertionError(e);
    }
  }

  /**
   * Initializes a class by name.
   *
   * @param className the class name
   */
  private static void initialize(String className)
  {
    try
    {
      Class.forName(className);
    }
    catch (ClassNotFoundException e)
    {
      throw new IllegalStateException("Unable to initialize " + className, e);
    }
  }

  /**
   * Provides access to {@link InstructionTestRunner} internal methods.
   */
  public interface InstructionTestRunnerAccess
  {
    /**
     * Computes the SHA-256 hex digest of the given bytes.
     *
     * @param bytes the bytes to hash
     * @return lowercase hex SHA-256 digest
     */
    String sha256Bytes(byte[] bytes);

    /**
     * Builds the grader argument array for ClaudeRunner invocation.
     * <p>
     * Exposed for testing to validate the --agent argument is correctly constructed.
     *
     * @param graderPromptFile the grader prompt file path
     * @param modelId the model ID to use
     * @param runnerWorktree the runner worktree path
     * @param jlinkBin the jlink binary path
     * @return the grader arguments array
     */
    String[] buildGraderArgs(Path graderPromptFile, String modelId, String runnerWorktree, Path jlinkBin);
  }

  /**
   * Provides access to {@link EmpiricalTestRunner} trial worktree management.
   */
  public interface EmpiricalTestRunnerAccess
  {
    /**
     * Creates an isolated git worktree for a single test run.
     *
     * @param baseRepo the base git repository to branch from
     * @return the path of the newly created worktree
     * @throws IOException if the worktree cannot be created
     */
    Path createTestWorktree(Path baseRepo) throws IOException;

    /**
     * Removes a test worktree.
     *
     * @param baseRepo     the base git repository
     * @param worktreePath the worktree path to remove
     */
    void removeTestWorktree(Path baseRepo, Path worktreePath);
  }

  /**
   * Provides access to {@link StatuslineCommand} context percent scaling.
   */
  @FunctionalInterface
  public interface StatuslineCommandAccess
  {
    /**
     * Scales the number of used tokens against the usable context window.
     *
     * @param usedTokens   the number of tokens used in the context window
     * @param totalContext the total context window size in tokens
     * @return the scaled percentage in the range [0, 100]
     */
    int scaleContextPercent(int usedTokens, int totalContext);
  }
}
