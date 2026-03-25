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
  // AbstractJvmScope is one of two permitted call sites for JsonMapper.builder(). The other is
  // AbstractClaudeHook.createStdinMapper() for the bootstrap case where no scope is yet available.
  // All other code must obtain the shared instance via JvmScope.getJsonMapper().
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

  @Override
  public Path getCatDir()
  {
    ensureOpen();
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
   * Returns the Claude config directory.
   * <p>
   * Subclasses provide the concrete value; {@link ClaudeTool} and {@link ClaudeHook} expose this
   * as part of their public API.
   *
   * @return the config directory path
   * @throws IllegalStateException if this scope is closed
   */
  protected abstract Path getClaudeConfigPath();

  @Override
  public Path getClaudeSessionsPath()
  {
    ensureOpen();
    return getClaudeConfigPath().resolve("projects").resolve(encodeProjectPath(getProjectPath().toString()));
  }

  @Override
  public Path getClaudeSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getClaudeSessionsPath().resolve(sessionId);
  }

  @Override
  public Path getCatWorkPath()
  {
    ensureOpen();
    return getProjectPath().resolve(".cat").resolve("work");
  }

  @Override
  public Path getCatSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    return getCatWorkPath().resolve("sessions").resolve(sessionId);
  }

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
