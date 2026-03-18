/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

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
 * Abstract base class providing default implementations of derived path methods and shared
 * lazy-initialized service instances for {@link JvmScope}.
 * <p>
 * Subclasses must implement the abstract methods declared in {@link JvmScope} to supply the base
 * configuration values from which these derived paths are computed.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
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
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<String> pluginPrefix = ConcurrentLazyReference.create(
    this::derivePluginPrefix);

  /**
   * Creates a new abstract JVM scope.
   */
  protected AbstractJvmScope()
  {
  }

  /**
   * Returns the {@code .cat} directory under the project's root directory.
   *
   * @return the path to the {@code .cat} directory
   * @throws AssertionError if the project directory is not configured
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getCatDir()
  {
    return getProjectPath().resolve(Config.CAT_DIR_NAME);
  }

  /**
   * Encodes a project directory path using Claude Code's encoding algorithm.
   * <p>
   * Replaces {@code /}, {@code .}, and spaces with {@code -}. For example,
   * {@code /workspace} encodes to {@code -workspace}, {@code /home/user/my.project}
   * encodes to {@code -home-user-my-project}, and {@code /home/user/my project}
   * encodes to {@code -home-user-my-project}.
   *
   * @param projectPath the project directory path to encode
   * @return the encoded project path
   * @throws NullPointerException if {@code projectPath} is null
   */
  public static String encodeProjectPath(String projectPath)
  {
    return projectPath.replace("/", "-").replace(".", "-").replace(" ", "-");
  }

  /**
   * Returns the base directory for session JSONL files.
   * <p>
   * Session files are stored at {@code {claudeSessionsPath}/{sessionId}.jsonl}.
   *
   * @return the session base directory path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getClaudeSessionsPath()
  {
    return getClaudeConfigDir().resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()));
  }

  /**
   * Returns the directory for a session's tracking files.
   * <p>
   * Located at {@code {claudeConfigDir}/projects/{encodedProjectRoot}/{sessionId}/}.
   *
   * @param sessionId the session ID
   * @return the session directory path
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getClaudeSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getClaudeSessionsPath().resolve(sessionId);
  }

  /**
   * Returns the cross-session project CAT directory.
   * <p>
   * Located at {@code {projectPath}/.cat/work/}.
   * <p>
   * This directory stores cross-session files such as {@code locks/} and {@code worktrees/}.
   *
   * @return the project CAT directory path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getCatWorkPath()
  {
    return getProjectPath().resolve(".cat").resolve("work");
  }

  /**
   * Returns the per-session CAT directory.
   * <p>
   * Located at {@code {projectPath}/.cat/work/sessions/{sessionId}/}.
   *
   * @param sessionId the session ID
   * @return the session CAT directory path
   * @throws NullPointerException if {@code sessionId} is null
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public Path getCatSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getCatWorkPath().resolve("sessions").resolve(sessionId);
  }

  /**
   * Returns the plugin prefix (e.g., {@code "cat"}).
   * <p>
   * Derived from the plugin root path structure ({@code .../{prefix}/{slug}/{version}/}).
   * The prefix is the directory component two levels above the version directory.
   *
   * @return the plugin prefix, never blank
   * @throws AssertionError if the prefix cannot be derived from the plugin root path
   * @throws IllegalStateException if this scope is closed
   */
  @Override
  public String getPluginPrefix()
  {
    ensureOpen();
    return pluginPrefix.getValue();
  }

  /**
   * Derives the plugin prefix from the plugin root path structure.
   *
   * @return the plugin prefix, never blank
   * @throws AssertionError if the prefix cannot be derived from the plugin root path
   */
  private String derivePluginPrefix()
  {
    Path pluginRoot = getPluginRoot().toAbsolutePath().normalize();
    Path slugDir = pluginRoot.getParent();
    if (slugDir == null)
      throw new AssertionError("Plugin root has no parent directory: " + pluginRoot);
    Path prefixDir = slugDir.getParent();
    if (prefixDir == null)
      throw new AssertionError("Plugin slug directory has no parent: " + slugDir);
    Path prefixName = prefixDir.getFileName();
    if (prefixName == null)
      throw new AssertionError("Cannot determine plugin prefix from path: " + pluginRoot +
        ". Expected structure: .../{prefix}/{slug}/{version}/");
    return prefixName.toString();
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
