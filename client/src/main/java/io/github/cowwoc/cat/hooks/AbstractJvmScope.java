/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.prompt.UserIssues;
import io.github.cowwoc.cat.hooks.read.post.DetectSequentialTools;
import io.github.cowwoc.cat.hooks.read.pre.PredictBatchOpportunity;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstract base class providing default implementations of derived path methods for {@link JvmScope}.
 * <p>
 * Subclasses must implement the abstract methods declared in {@link JvmScope} to supply the base
 * configuration values from which these derived paths are computed.
 */
public abstract class AbstractJvmScope implements JvmScope
{
  private final ConcurrentLazyReference<JsonMapper> jsonMapper = ConcurrentLazyReference.create(() ->
    JsonMapper.builder().
      enable(SerializationFeature.INDENT_OUTPUT).
      build());
  private final ConcurrentLazyReference<YAMLMapper> yamlMapper = ConcurrentLazyReference.create(() ->
    YAMLMapper.builder().build());
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<DisplayUtils> displayUtils = ConcurrentLazyReference.create(() ->
  {
    try
    {
      return new DisplayUtils(this);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  });
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<DetectSequentialTools> detectSequentialTools =
    ConcurrentLazyReference.create(() -> new DetectSequentialTools(this));
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<PredictBatchOpportunity> predictBatchOpportunity =
    ConcurrentLazyReference.create(() -> new PredictBatchOpportunity(this));
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(this));

  /**
   * Creates a new abstract JVM scope.
   */
  protected AbstractJvmScope()
  {
  }

  /**
   * Encodes a project directory path using Claude Code's encoding algorithm.
   * <p>
   * Replaces {@code /} and {@code .} with {@code -}. For example,
   * {@code /workspace} encodes to {@code -workspace}, and {@code /home/user/my.project}
   * encodes to {@code -home-user-my-project}.
   *
   * @param projectPath the project directory path to encode
   * @return the encoded project path
   * @throws NullPointerException if {@code projectPath} is null
   */
  public static String encodeProjectPath(String projectPath)
  {
    return projectPath.replace("/", "-").replace(".", "-");
  }

  /**
   * Returns the encoded project directory name.
   * <p>
   * Applies the Claude Code project directory encoding: replaces {@code /} and {@code .} with {@code -}.
   * For example, {@code /workspace} encodes to {@code -workspace}.
   *
   * @return the encoded project directory name
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public String getEncodedProjectDir()
  {
    return encodeProjectPath(getClaudeProjectDir().toString());
  }

  /**
   * Returns the base directory for session JSONL files.
   * <p>
   * Session files are stored at {@code {sessionBasePath}/{sessionId}.jsonl}.
   *
   * @return the session base directory path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getSessionBasePath()
  {
    return getClaudeConfigDir().resolve("projects").resolve(getEncodedProjectDir());
  }

  /**
   * Returns the directory for the current session's tracking files.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/}.
   *
   * @return the session directory path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getSessionDirectory()
  {
    return getSessionBasePath().resolve(getClaudeSessionId());
  }

  /**
   * Returns the cross-session project CAT directory.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectDir}/cat/}, where {@code {encodedProjectDir}} is the
   * project directory path with {@code /} and {@code .} replaced by {@code -}.
   * <p>
   * This directory stores cross-session files such as {@code locks/} and {@code worktrees/}.
   *
   * @return the project CAT directory path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getProjectCatDir()
  {
    return getSessionBasePath().resolve("cat");
  }

  /**
   * Returns the per-session CAT directory.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectDir}/{sessionId}/cat/}, where
   * {@code {encodedProjectDir}} is the project directory path with {@code /} and {@code .} replaced by {@code -}.
   * <p>
   * This directory stores session-scoped files such as {@code verify/} and {@code e2e-config-test/}.
   *
   * @return the session CAT directory path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getSessionCatDir()
  {
    return getSessionDirectory().resolve("cat");
  }

  @Override
  public JsonMapper getJsonMapper()
  {
    ensureOpen();
    return jsonMapper.getValue();
  }

  @Override
  public YAMLMapper getYamlMapper()
  {
    ensureOpen();
    return yamlMapper.getValue();
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    ensureOpen();
    return displayUtils.getValue();
  }

  @Override
  public DetectSequentialTools getDetectSequentialTools()
  {
    ensureOpen();
    return detectSequentialTools.getValue();
  }

  @Override
  public PredictBatchOpportunity getPredictBatchOpportunity()
  {
    ensureOpen();
    return predictBatchOpportunity.getValue();
  }

  @Override
  public UserIssues getUserIssues()
  {
    ensureOpen();
    return userIssues.getValue();
  }

  /**
   * Throws an exception if this scope has been closed.
   *
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public void ensureOpen()
  {
    if (isClosed())
      throw new IllegalStateException("this scope is closed");
  }
}
