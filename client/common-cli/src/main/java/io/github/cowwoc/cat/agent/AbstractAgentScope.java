/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class providing engine-neutral implementations of derived path methods and shared
 * lazy-initialized service instances for {@link AgentScope}.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public abstract class AbstractAgentScope implements AgentScope
{
  // Initialized at field declaration so it is available before any subclass constructor runs.
  // This prevents NullPointerException when superclass methods call isClosed() during construction.
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ConcurrentLazyReference<JsonMapper> jsonMapper = ConcurrentLazyReference.create(() ->
    JsonMapper.builder().
      enable(SerializationFeature.INDENT_OUTPUT).
      build());
  private final ConcurrentLazyReference<YAMLMapper> yamlMapper = ConcurrentLazyReference.create(() ->
    YAMLMapper.builder().build());
  private final Path projectPath;
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<Path> catDir = ConcurrentLazyReference.create(() ->
    getProjectPath().resolve(Config.CAT_DIR_NAME));
  @SuppressWarnings("this-escape")
  private final ConcurrentLazyReference<Path> catWorkPath = ConcurrentLazyReference.create(() ->
  {
    try
    {
      Path project = getProjectPath();
      Config config = Config.load(getJsonMapper(), project);
      String workPathStr = config.getString("workPath", "${CAT_PROJECT_DIR}/.cat/work");
      if (workPathStr.contains("${CLAUDE_PROJECT_DIR}"))
      {
        throw new IllegalArgumentException(
          "workPath must use ${CAT_PROJECT_DIR}; ${CLAUDE_PROJECT_DIR} is not supported");
      }
      String expandedPath = workPathStr.
        replace("${CAT_PROJECT_DIR}", project.toString());

      if (expandedPath.startsWith("~"))
      {
        String home = System.getProperty("user.home");
        expandedPath = home + expandedPath.substring(1);
      }

      return Path.of(expandedPath);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  });

  /**
   * Creates a new abstract agent scope with the given base path.
   *
   * @param projectPath the project's root directory
   * @throws NullPointerException if {@code projectPath} is null
   */
  protected AbstractAgentScope(Path projectPath)
  {
    requireThat(projectPath, "projectPath").isNotNull();
    this.projectPath = projectPath;
  }

  @Override
  public Path getProjectPath()
  {
    ensureOpen();
    return projectPath;
  }

  @Override
  public Path getCatDir()
  {
    ensureOpen();
    return catDir.getValue();
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
    return catWorkPath.getValue();
  }

  @Override
  public Path getCatSessionPath(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    Path sessionsDir = getCatWorkPath().resolve("sessions").normalize();
    Path result = sessionsDir.resolve(sessionId).normalize();
    if (!result.startsWith(sessionsDir))
      throw new IllegalArgumentException("sessionId would escape sessions directory: " + sessionId);
    return result;
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
  public boolean isClosed()
  {
    return closed.get();
  }

  @Override
  public void close()
  {
    closed.set(true);
  }

  @Override
  public void ensureOpen()
  {
    if (isClosed())
      throw new IllegalStateException("this scope is closed");
  }
}
