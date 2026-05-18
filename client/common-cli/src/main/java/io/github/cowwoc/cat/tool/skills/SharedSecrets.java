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
  private static SprtRuntimeRunnerAccess sprtRuntimeRunnerAccess;
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
   * Collects session files (main and nested agent sessions) for an empirical test run.
   *
   * @param sessionsPath the root sessions directory
   * @param sessionId the session identifier
   * @return ordered session files (main first, then nested)
   * @throws NullPointerException if {@code sessionsPath} or {@code sessionId} are null
   * @throws IOException if session discovery fails
   */
  public static java.util.List<Path> collectSessionFiles(Path sessionsPath, String sessionId) throws IOException
  {
    requireThat(sessionsPath, "sessionsPath").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    if (empiricalTestRunnerAccess == null)
      initialize(EmpiricalTestRunner.class);
    return empiricalTestRunnerAccess.collectSessionFiles(sessionsPath, sessionId);
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
   * Parses {@code run-sprt} arguments for tests.
   *
   * @param args the raw command arguments
   * @return {@code [worktree_path, test_dir, test_model, effort, session_id]}
   */
  public static String[] parseRunSprtArgs(String[] args)
  {
    requireThat(args, "args").isNotNull();
    if (instructionTestRunnerAccess == null)
      initialize(InstructionTestRunner.class);
    return instructionTestRunnerAccess.parseRunSprtArgs(args);
  }

  /**
   * Builds Claude trial runner arguments for tests.
   *
   * @param promptFile     the prompt file
   * @param modelId        the model ID
   * @param effort         the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson     the output JSON path
   * @param jlinkBin       the jlink binary directory
   * @return the runner arguments
   */
  public static String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path jlinkBin)
  {
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.buildClaudeTrialArgs(promptFile, modelId, effort,
      runnerWorktree, outputJson, jlinkBin);
  }

  /**
   * Builds Codex trial runner arguments for tests.
   *
   * @param promptFile     the prompt file
   * @param modelId        the model ID
   * @param effort         the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson     the output JSON path
   * @return the runner arguments
   */
  public static String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson)
  {
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.buildCodexTrialArgs(promptFile, modelId, effort,
      runnerWorktree, outputJson);
  }

  /**
   * Builds Claude grader arguments for tests.
   *
   * @param graderPromptFile the grader prompt file
   * @param modelId          the model ID
   * @param effort           the reasoning effort
   * @param runnerWorktree   the runner worktree
   * @param jlinkBin         the jlink binary directory
   * @return the grader arguments
   */
  public static String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree, Path jlinkBin)
  {
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.buildClaudeGraderArgs(graderPromptFile, modelId, effort,
      runnerWorktree, jlinkBin);
  }

  /**
   * Builds Codex grader arguments for tests.
   *
   * @param graderPromptFile the grader prompt file
   * @param modelId          the model ID
   * @param effort           the reasoning effort
   * @param runnerWorktree   the runner worktree
   * @return the grader arguments
   */
  public static String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
    String runnerWorktree)
  {
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.buildCodexGraderArgs(graderPromptFile, modelId, effort,
      runnerWorktree);
  }

  /**
   * Resolves an SPRT runtime descriptor for tests.
   *
   * @param descriptor the plugin descriptor path
   * @return the runtime identifier
   */
  public static String sprtRuntimeIdForDescriptor(Path descriptor)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.runtimeIdForDescriptor(descriptor);
  }

  /**
   * Builds runtime-dispatched trial arguments for tests.
   *
   * @param descriptor     the plugin descriptor path
   * @param promptFile     the prompt file
   * @param modelId        the model ID
   * @param effort         the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson     the output JSON path
   * @return the runner arguments
   */
  public static String[] buildTrialArgsForDescriptor(Path descriptor, Path promptFile,
    String modelId, String effort, String runnerWorktree, String outputJson)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.buildTrialArgsForDescriptor(descriptor, promptFile, modelId,
      effort, runnerWorktree, outputJson);
  }

  /**
   * Builds runtime-dispatched grader arguments for tests.
   *
   * @param descriptor       the plugin descriptor path
   * @param graderPromptFile the grader prompt file
   * @param modelId          the model ID
   * @param effort           the reasoning effort
   * @param runnerWorktree   the runner worktree
   * @return the runner arguments
   */
  public static String[] buildGraderArgsForDescriptor(Path descriptor, Path graderPromptFile,
    String modelId, String effort, String runnerWorktree)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    if (sprtRuntimeRunnerAccess == null)
      initialize(SprtRuntimeRunner.class);
    return sprtRuntimeRunnerAccess.buildGraderArgsForDescriptor(descriptor, graderPromptFile,
      modelId, effort, runnerWorktree);
  }

  /**
   * Registers the access object for {@link SprtRuntimeRunner}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setSprtRuntimeRunnerAccess(SprtRuntimeRunnerAccess access)
  {
    requireThat(access, "access").isNotNull();
    sprtRuntimeRunnerAccess = access;
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
     * Parses {@code run-sprt} arguments.
     *
     * @param args the raw command arguments
     * @return {@code [worktree_path, test_dir, test_model, effort, session_id]}
     */
    String[] parseRunSprtArgs(String[] args);
  }

  /**
   * Provides access to {@link SprtRuntimeRunner} internal methods.
   */
  public interface SprtRuntimeRunnerAccess
  {
    /**
     * Builds Claude trial runner arguments.
     *
     * @param promptFile     the prompt file
     * @param modelId        the model ID
     * @param effort         the reasoning effort
     * @param runnerWorktree the runner worktree
     * @param outputJson     the output JSON path
     * @param jlinkBin       the jlink binary directory
     * @return the runner arguments
     */
    String[] buildClaudeTrialArgs(Path promptFile, String modelId, String effort,
      String runnerWorktree, String outputJson, Path jlinkBin);

    /**
     * Builds Codex trial runner arguments.
     *
     * @param promptFile     the prompt file
     * @param modelId        the model ID
     * @param effort         the reasoning effort
     * @param runnerWorktree the runner worktree
     * @param outputJson     the output JSON path
     * @return the runner arguments
     */
    String[] buildCodexTrialArgs(Path promptFile, String modelId, String effort,
      String runnerWorktree, String outputJson);

    /**
     * Builds Claude grader arguments.
     *
     * @param graderPromptFile the grader prompt file
     * @param modelId          the model ID
     * @param effort           the reasoning effort
     * @param runnerWorktree   the runner worktree
     * @param jlinkBin         the jlink binary directory
     * @return the grader arguments
     */
    String[] buildClaudeGraderArgs(Path graderPromptFile, String modelId, String effort,
      String runnerWorktree, Path jlinkBin);

    /**
     * Builds Codex grader arguments.
     *
     * @param graderPromptFile the grader prompt file
     * @param modelId          the model ID
     * @param effort           the reasoning effort
     * @param runnerWorktree   the runner worktree
     * @return the grader arguments
     */
    String[] buildCodexGraderArgs(Path graderPromptFile, String modelId, String effort,
      String runnerWorktree);

    /**
     * Resolves an SPRT runtime descriptor.
     *
     * @param descriptor the plugin descriptor path
     * @return the runtime identifier
     */
    String runtimeIdForDescriptor(Path descriptor);

    /**
     * Builds runtime-dispatched trial arguments.
     *
     * @param descriptor     the plugin descriptor path
     * @param promptFile     the prompt file
     * @param modelId        the model ID
     * @param effort         the reasoning effort
     * @param runnerWorktree the runner worktree
     * @param outputJson     the output JSON path
     * @return the runner arguments
     */
    String[] buildTrialArgsForDescriptor(Path descriptor, Path promptFile, String modelId,
      String effort, String runnerWorktree, String outputJson);

    /**
     * Builds runtime-dispatched grader arguments.
     *
     * @param descriptor       the plugin descriptor path
     * @param graderPromptFile the grader prompt file
     * @param modelId          the model ID
     * @param effort           the reasoning effort
     * @param runnerWorktree   the runner worktree
     * @return the runner arguments
     */
    String[] buildGraderArgsForDescriptor(Path descriptor, Path graderPromptFile, String modelId,
      String effort, String runnerWorktree);
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

    /**
     * Collects session files for a session ID.
     *
     * @param sessionsPath the root sessions directory
     * @param sessionId the session identifier
     * @return ordered session files (main first, then nested)
     * @throws IOException if session discovery fails
     */
    java.util.List<Path> collectSessionFiles(Path sessionsPath, String sessionId) throws IOException;
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
