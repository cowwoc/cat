/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.util.IssueDiscovery;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
  private static ClaudeEnvAccess claudeEnvAccess;

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
   * Parses the status field from STATE.md lines and validates it against canonical values.
   *
   * @param lines the lines from the STATE.md file
   * @param statePath the path to the STATE.md file (used in error messages only)
   * @return the validated status string
   * @throws NullPointerException if {@code lines} or {@code statePath} are null
   * @throws IOException if the status field is missing or the status value is non-canonical
   */
  public static String getIssueStatus(List<String> lines, Path statePath) throws IOException
  {
    requireThat(lines, "lines").isNotNull();
    requireThat(statePath, "statePath").isNotNull();
    if (issueDiscoveryAccess == null)
      initialize(IssueDiscovery.class);
    return issueDiscoveryAccess.getIssueStatus(lines, statePath);
  }

  /**
   * Registers the access object for {@link ClaudeEnv}.
   *
   * @param access the access object
   * @throws NullPointerException if {@code access} is null
   */
  public static void setClaudeEnvAccess(ClaudeEnvAccess access)
  {
    requireThat(access, "access").isNotNull();
    claudeEnvAccess = access;
  }

  /**
   * Creates a new {@link ClaudeEnv} with the specified environment map.
   * <p>
   * This is intended for testing, where a controlled environment map avoids dependencies on the
   * host environment.
   *
   * @param env the environment variable map to use
   * @return a new ClaudeEnv
   * @throws NullPointerException if {@code env} is null
   */
  public static ClaudeEnv newClaudeEnv(Map<String, String> env)
  {
    if (claudeEnvAccess == null)
      initialize(ClaudeEnv.class);
    return claudeEnvAccess.newClaudeEnv(env);
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
     * Gets the issue status from STATE.md lines.
     *
     * @param lines the lines
     * @param statePath the path
     * @return the status
     * @throws IOException if parsing fails
     */
    String getIssueStatus(List<String> lines, Path statePath) throws IOException;
  }

  /**
   * Provides test-specific access to {@link ClaudeEnv}.
   */
  @FunctionalInterface
  public interface ClaudeEnvAccess
  {
    /**
     * Creates a new {@link ClaudeEnv} with the specified environment map.
     *
     * @param env the environment variable map
     * @return a new ClaudeEnv
     */
    ClaudeEnv newClaudeEnv(Map<String, String> env);
  }
}
