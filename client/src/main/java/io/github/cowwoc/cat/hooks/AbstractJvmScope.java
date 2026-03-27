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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class providing default implementations of derived path methods and shared
 * lazy-initialized service instances for {@link JvmScope}.
 * <p>
 * Stores the JVM-level base path values ({@code projectPath}, {@code pluginRoot})
 * so that subclasses do not need to duplicate those fields. Claude-specific paths
 * (such as the Claude config directory) belong in {@link AbstractClaudeScope}.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractJvmScope implements JvmScope
{
  // Initialized at field declaration so it is available before any subclass constructor runs.
  // This prevents NullPointerException when superclass methods call isClosed() during construction.
  private final AtomicBoolean closed = new AtomicBoolean();
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
  private final Path projectPath;
  private final Path pluginRoot;

  /**
   * Creates a new abstract JVM scope with the given base paths.
   *
   * @param projectPath the project's root directory
   * @param pluginRoot the Claude plugin root directory
   * @throws NullPointerException if any parameter is null
   */
  protected AbstractJvmScope(Path projectPath, Path pluginRoot)
  {
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    this.projectPath = projectPath;
    this.pluginRoot = pluginRoot;
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return projectPath;
  }

  @Override
  public Path getPluginRoot()
  {
    ensureOpen();
    return pluginRoot;
  }

  @Override
  public Path getCatDir()
  {
    ensureOpen();
    return projectPath.resolve(Config.CAT_DIR_NAME);
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

  @Override
  public Path getCatWorkPath()
  {
    ensureOpen();
    return projectPath.resolve(".cat").resolve("work");
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
    Path pluginRoot = this.pluginRoot.toAbsolutePath().normalize();
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

  @Override
  public boolean isClosed()
  {
    return closed.get();
  }

  @Override
  public void close()
  {
    closed.set(true);
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
