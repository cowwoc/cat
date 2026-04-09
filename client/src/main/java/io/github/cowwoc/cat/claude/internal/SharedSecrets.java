/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.internal;

import io.github.cowwoc.cat.claude.hook.skills.EmpiricalTestRunner;
import io.github.cowwoc.cat.claude.hook.skills.InstructionTestRunner;
import io.github.cowwoc.cat.claude.hook.util.IssueDiscovery;
import io.github.cowwoc.cat.claude.hook.util.StatuslineCommand;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
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
  private static PostToolUseHookAccess postToolUseHookAccess;
  private static PostToolUseFailureHookAccess postToolUseFailureHookAccess;
  private static IssueDiscoveryAccess issueDiscoveryAccess;
  private static EmpiricalTestRunnerAccess empiricalTestRunnerAccess;
  private static InstructionTestRunnerAccess instructionTestRunnerAccess;
  private static StatuslineCommandAccess statuslineCommandAccess;

  private SharedSecrets()
  {
  }

  /**
   * Registers the access object for {@link PostToolUseHook}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setPostToolUseHookAccess(PostToolUseHookAccess access)
  {
    requireThat(access, "access").isNotNull();
    postToolUseHookAccess = access;
  }

  /**
   * Creates a new {@link PostToolUseHook} with the specified handlers.
   *
   * @param handlers the handlers to use
   * @return a new PostToolUseHook
   * @throws NullPointerException if {@code handlers} is null
   */
  public static PostToolUseHook newPostToolUseHook(List<PostToolHandler> handlers)
  {
    if (postToolUseHookAccess == null)
      initialize(PostToolUseHook.class);
    return postToolUseHookAccess.newPostToolUseHook(handlers);
  }

  /**
   * Registers the access object for {@link PostToolUseFailureHook}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setPostToolUseFailureHookAccess(PostToolUseFailureHookAccess access)
  {
    requireThat(access, "access").isNotNull();
    postToolUseFailureHookAccess = access;
  }

  /**
   * Creates a new {@link PostToolUseFailureHook} with the specified handlers.
   *
   * @param handlers the handlers to use
   * @return a new PostToolUseFailureHook
   * @throws NullPointerException if {@code handlers} is null
   */
  public static PostToolUseFailureHook newPostToolUseFailureHook(List<PostToolHandler> handlers)
  {
    if (postToolUseFailureHookAccess == null)
      initialize(PostToolUseFailureHook.class);
    return postToolUseFailureHookAccess.newPostToolUseFailureHook(handlers);
  }

  /**
   * Registers the access object for {@link IssueDiscovery}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setIssueDiscoveryAccess(IssueDiscoveryAccess access)
  {
    requireThat(access, "access").isNotNull();
    issueDiscoveryAccess = access;
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
    if (issueDiscoveryAccess == null)
      initialize(IssueDiscovery.class);
    return issueDiscoveryAccess.getIssueStatus(content, indexPath, mapper);
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
   * Registers the access object for {@link StatuslineCommand}.
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
      initialize(StatuslineCommand.class);
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
   * Provides test-specific access to {@link PostToolUseHook}.
   */
  @FunctionalInterface
  public interface PostToolUseHookAccess
  {
    /**
     * Creates a new {@link PostToolUseHook} with the specified handlers.
     *
     * @param handlers the handlers
     * @return a new PostToolUseHook
     */
    PostToolUseHook newPostToolUseHook(List<PostToolHandler> handlers);
  }

  /**
   * Provides test-specific access to {@link PostToolUseFailureHook}.
   */
  @FunctionalInterface
  public interface PostToolUseFailureHookAccess
  {
    /**
     * Creates a new {@link PostToolUseFailureHook} with the specified handlers.
     *
     * @param handlers the handlers
     * @return a new PostToolUseFailureHook
     */
    PostToolUseFailureHook newPostToolUseFailureHook(List<PostToolHandler> handlers);
  }

  /**
   * Provides access to {@link IssueDiscovery}.
   */
  @FunctionalInterface
  public interface IssueDiscoveryAccess
  {
    /**
     * Gets the issue status from index.json content.
     *
     * @param content the JSON content of the index.json file
     * @param indexPath the path to the index.json file (used in error messages only)
     * @param mapper the JSON mapper to use for parsing
     * @return the validated status string, or {@code "open"} if absent
     * @throws IOException if parsing fails
     */
    String getIssueStatus(String content, Path indexPath, JsonMapper mapper) throws IOException;
  }

  /**
   * Provides access to {@link InstructionTestRunner} cryptographic helpers.
   */
  @FunctionalInterface
  public interface InstructionTestRunnerAccess
  {
    /**
     * Computes the SHA-256 hex digest of the given bytes.
     *
     * @param bytes the bytes to hash
     * @return lowercase hex SHA-256 digest
     */
    String sha256Bytes(byte[] bytes);
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
