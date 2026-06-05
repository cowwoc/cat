/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import io.github.cowwoc.cat.tool.CliTool;
import io.github.cowwoc.cat.tool.util.IssueDiscovery;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.util.List;

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
  private static SprtRunnerAccess sprtRunnerAccess;
  private static StatuslineCommandAccess statuslineCommandAccess;

  /**
   * A model and reasoning effort pair.
   *
   * @param modelId the model ID
   * @param effort  the reasoning effort
   */
  public record ModelEffort(String modelId, String effort)
  {
    /**
     * Creates a new pair.
     *
     * @param modelId the model ID
     * @param effort  the reasoning effort
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if any argument is blank
     */
    public ModelEffort
    {
      requireThat(modelId, "modelId").isNotBlank();
      requireThat(effort, "effort").isNotBlank();
    }
  }

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
   * Registers the access object for {@link SprtRunner}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setSprtRunnerAccess(SprtRunnerAccess access)
  {
    requireThat(access, "access").isNotNull();
    sprtRunnerAccess = access;
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
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.sha256Bytes(bytes);
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
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.parseRunSprtArgs(args);
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
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(jlinkBin, "jlinkBin").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildClaudeTrialArgs(promptFile, modelId, effort,
      runnerWorktree, outputJson, jlinkBin);
  }

  /**
   * Builds Claude trial runner arguments with a persisted session file.
   *
   * @param promptFile     the prompt file
   * @param modelId        the model ID
   * @param effort         the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson     the output JSON path
   * @param jlinkBin       the jlink binary directory
   * @param sessionFile    the persisted session file path
   * @return the runner arguments
   */
  public static String[] buildClaudeSessionTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path jlinkBin, Path sessionFile)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(jlinkBin, "jlinkBin").isNotNull();
    requireThat(sessionFile, "sessionFile").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildClaudeSessionTrialArgs(promptFile, modelId, effort,
      runnerWorktree, outputJson, jlinkBin, sessionFile);
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
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildCodexTrialArgs(promptFile, modelId, effort,
      runnerWorktree, outputJson);
  }

  /**
   * Builds Codex trial runner arguments with a persisted session file.
   *
   * @param promptFile     the prompt file
   * @param modelId        the model ID
   * @param effort         the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson     the output JSON path
   * @param sessionFile    the persisted session file path
   * @return the runner arguments
   */
  public static String[] buildCodexSessionTrialArgs(Path promptFile, String modelId, String effort,
    String runnerWorktree, String outputJson, Path sessionFile)
  {
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(sessionFile, "sessionFile").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildCodexSessionTrialArgs(promptFile, modelId, effort,
      runnerWorktree, outputJson, sessionFile);
  }

  /**
   * Invokes multi-turn trial execution for tests.
   *
   * @param runner         the runner instance
   * @param promptFiles    the ordered prompt files
   * @param modelId        the model ID
   * @param effort         the reasoning effort
   * @param runnerWorktree the runner worktree
   * @param outputJson     the output JSON path
   * @param logStream      the log stream
   * @return the process exit code
   * @throws IOException if execution fails
   */
  public static int runTrial(SprtRunner runner, List<Path> promptFiles, String modelId, String effort,
    String runnerWorktree, String outputJson, PrintStream logStream) throws IOException
  {
    requireThat(runner, "runner").isNotNull();
    requireThat(promptFiles, "promptFiles").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    requireThat(logStream, "logStream").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.runTrial(runner, promptFiles, modelId, effort, runnerWorktree,
      outputJson, logStream);
  }

  /**
   * Invokes the nested engine launcher for tests.
   *
   * @param runner         the runner instance
   * @param args           the launcher arguments
   * @param runnerWorktree the runner worktree
   * @param out            receives launcher output
   * @return the nested runner exit code
   * @throws IOException if execution fails
   */
  public static int runSprtEngineCommand(SprtRunner runner, String[] args, String runnerWorktree,
    PrintStream out) throws IOException
  {
    requireThat(runner, "runner").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(out, "out").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.runEngineCommand(runner, args, runnerWorktree, out);
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
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(jlinkBin, "jlinkBin").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildClaudeGraderArgs(graderPromptFile, modelId, effort,
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
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildCodexGraderArgs(graderPromptFile, modelId, effort,
      runnerWorktree);
  }

  /**
   * Resolves an SPRT engine descriptor for tests.
   *
   * @param descriptor the plugin descriptor path
   * @return the engine identifier
   */
  public static String sprtEngineIdForDescriptor(Path descriptor)
  {
    requireThat(descriptor, "descriptor").isNotNull();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.engineIdForDescriptor(descriptor);
  }

  /**
   * Resolves the fixed instruction-grader model and effort for tests.
   *
   * @param pluginRoot        the CAT plugin root
   * @param descriptor        the plugin descriptor path
   * @param claudeCodeVersion the Claude Code version for Claude short-name resolution
   * @return the model and effort
   * @throws IOException if the grader descriptor cannot be read
   */
  public static ModelEffort resolveGraderModelEffort(Path pluginRoot, Path descriptor,
    String claudeCodeVersion) throws IOException
  {
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(descriptor, "descriptor").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.resolveGraderModelEffort(pluginRoot, descriptor,
      claudeCodeVersion);
  }

  /**
   * Runs the grader path for tests.
   *
   * @param scope             the active CLI scope
   * @param claudeCodeVersion the Claude Code version for Claude short-name resolution
   * @param graderPromptFile  the grader prompt file
   * @param modelId           the caller-supplied model ID
   * @param effort            the caller-supplied effort
   * @param runnerWorktree    the runner worktree
   * @param gradeOutputPath   the grade output path
   * @param out               receives runner output
   * @return the nested runner exit code
   * @throws IOException if the grader command cannot be run
   */
  public static int runGrader(CliTool scope, String claudeCodeVersion,
    Path graderPromptFile, String modelId, String effort, String runnerWorktree,
    String gradeOutputPath, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    SprtRunner runner = new SprtRunner(scope, claudeCodeVersion);
    return runner.runGrader(graderPromptFile, modelId, effort, runnerWorktree,
      gradeOutputPath, out);
  }

  /**
   * Builds engine-dispatched trial arguments for tests.
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
    requireThat(promptFile, "promptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    requireThat(outputJson, "outputJson").isNotBlank();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildTrialArgsForDescriptor(descriptor, promptFile, modelId,
      effort, runnerWorktree, outputJson);
  }

  /**
   * Builds engine-dispatched grader arguments for tests.
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
    requireThat(graderPromptFile, "graderPromptFile").isNotNull();
    requireThat(modelId, "modelId").isNotBlank();
    requireThat(effort, "effort").isNotBlank();
    requireThat(runnerWorktree, "runnerWorktree").isNotBlank();
    if (sprtRunnerAccess == null)
      initialize(SprtRunner.class);
    return sprtRunnerAccess.buildGraderArgsForDescriptor(descriptor, graderPromptFile,
      modelId, effort, runnerWorktree);
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
   * Provides access to {@link SprtRunner} internal methods.
   */
  public interface SprtRunnerAccess
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
     * Builds Claude trial runner arguments with a persisted session file.
     *
     * @param promptFile     the prompt file
     * @param modelId        the model ID
     * @param effort         the reasoning effort
     * @param runnerWorktree the runner worktree
     * @param outputJson     the output JSON path
     * @param jlinkBin       the jlink binary directory
     * @param sessionFile    the persisted session file path
     * @return the runner arguments
     */
    String[] buildClaudeSessionTrialArgs(Path promptFile, String modelId, String effort,
      String runnerWorktree, String outputJson, Path jlinkBin, Path sessionFile);

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
     * Builds Codex trial runner arguments with a persisted session file.
     *
     * @param promptFile     the prompt file
     * @param modelId        the model ID
     * @param effort         the reasoning effort
     * @param runnerWorktree the runner worktree
     * @param outputJson     the output JSON path
     * @param sessionFile    the persisted session file path
     * @return the runner arguments
     */
    String[] buildCodexSessionTrialArgs(Path promptFile, String modelId, String effort,
      String runnerWorktree, String outputJson, Path sessionFile);

    /**
     * Invokes multi-turn trial execution for tests.
     *
     * @param runner         the runner instance
     * @param promptFiles    the ordered prompt files
     * @param modelId        the model ID
     * @param effort         the reasoning effort
     * @param runnerWorktree the runner worktree
     * @param outputJson     the output JSON path
     * @param logStream      the log stream
     * @return the process exit code
     * @throws IOException if execution fails
     */
    int runTrial(SprtRunner runner, List<Path> promptFiles, String modelId, String effort,
      String runnerWorktree, String outputJson, PrintStream logStream) throws IOException;

    /**
     * Invokes the nested engine launcher.
     *
     * @param runner         the runner instance
     * @param args           the launcher arguments
     * @param runnerWorktree the runner worktree
     * @param out            receives launcher output
     * @return the nested runner exit code
     * @throws IOException if execution fails
     */
    int runEngineCommand(SprtRunner runner, String[] args, String runnerWorktree, PrintStream out)
      throws IOException;

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
     * Resolves an SPRT engine descriptor.
     *
     * @param descriptor the plugin descriptor path
     * @return the engine identifier
     */
    String engineIdForDescriptor(Path descriptor);

    /**
     * Resolves the fixed instruction-grader model and effort.
     *
     * @param pluginRoot        the CAT plugin root
     * @param descriptor        the plugin descriptor path
     * @param claudeCodeVersion the Claude Code version for Claude short-name resolution
     * @return the model and effort
     * @throws IOException if the grader descriptor cannot be read
     */
    ModelEffort resolveGraderModelEffort(Path pluginRoot, Path descriptor,
      String claudeCodeVersion) throws IOException;

    /**
     * Builds engine-dispatched trial arguments.
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
     * Builds engine-dispatched grader arguments.
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
