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

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production implementation of JvmScope.
 * <p>
 * Provides lazy-loaded DisplayUtils and environment paths via ConcurrentLazyReference
 * for thread-safe initialization. Reads {@code CLAUDE_PROJECT_DIR} and
 * {@code CLAUDE_PLUGIN_ROOT} from the process environment.
 */
public final class MainJvmScope implements JvmScope
{
  private final ConcurrentLazyReference<JsonMapper> jsonMapper = ConcurrentLazyReference.create(() ->
    JsonMapper.builder().
      enable(SerializationFeature.INDENT_OUTPUT).
      build());
  private final ConcurrentLazyReference<DisplayUtils> displayUtils = ConcurrentLazyReference.create(() ->
  {
    try
    {
      return new DisplayUtils(jsonMapper.getValue());
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  });
  private final ConcurrentLazyReference<DetectSequentialTools> detectSequentialTools =
    ConcurrentLazyReference.create(() -> new DetectSequentialTools(getJsonMapper()));
  private final ConcurrentLazyReference<PredictBatchOpportunity> predictBatchOpportunity =
    ConcurrentLazyReference.create(() -> new PredictBatchOpportunity(getJsonMapper()));
  private final ConcurrentLazyReference<UserIssues> userIssues =
    ConcurrentLazyReference.create(() -> new UserIssues(getJsonMapper()));
  private final ConcurrentLazyReference<Path> claudeProjectDir = ConcurrentLazyReference.create(() ->
  {
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    if (projectDir == null || projectDir.isEmpty())
      throw new AssertionError("CLAUDE_PROJECT_DIR is not set");
    return Path.of(projectDir);
  });
  private final ConcurrentLazyReference<Path> claudePluginRoot = ConcurrentLazyReference.create(() ->
  {
    String pluginRoot = System.getenv("CLAUDE_PLUGIN_ROOT");
    if (pluginRoot == null || pluginRoot.isEmpty())
      throw new AssertionError("CLAUDE_PLUGIN_ROOT is not set");
    return Path.of(pluginRoot);
  });
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a new production JVM scope.
   */
  public MainJvmScope()
  {
  }

  @Override
  public DisplayUtils getDisplayUtils()
  {
    ensureOpen();
    return displayUtils.getValue();
  }

  @Override
  public Path getClaudeProjectDir()
  {
    ensureOpen();
    return claudeProjectDir.getValue();
  }

  @Override
  public Path getClaudePluginRoot()
  {
    ensureOpen();
    return claudePluginRoot.getValue();
  }

  @Override
  public JsonMapper getJsonMapper()
  {
    ensureOpen();
    return jsonMapper.getValue();
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
}
