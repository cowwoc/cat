/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AbstractClaudeHook;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.skills.TerminalType;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test implementation of {@link ClaudeHook} with injectable hook JSON payload and environment paths.
 * <p>
 * Accepts the hook JSON payload and infrastructure paths as constructor parameters so tests do not
 * depend on stdin or environment variables.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class TestClaudeHook extends AbstractClaudeHook
{
  private static final String DEFAULT_SESSION_ID = "00000000-0000-0000-0000-000000000000";

  private final TerminalType terminalType;
  private final Path workDir;

  /**
   * Creates a new test hook scope with auto-generated temporary directories and a default empty
   * hook payload containing only the default session ID.
   * <p>
   * The caller is responsible for deleting the temporary directories when done.
   */
  public TestClaudeHook()
  {
    this(createTempDirs());
  }

  /**
   * Creates a new test hook scope with auto-generated temporary directories and the specified hook payload.
   * <p>
   * The caller is responsible for deleting the temporary directories when done.
   *
   * @param payloadJson the hook input JSON string (must contain a {@code session_id} field)
   * @throws NullPointerException if {@code payloadJson} is null
   * @throws IllegalArgumentException if {@code payloadJson} is blank
   * @throws IllegalStateException if the JSON is malformed or missing required fields
   */
  public TestClaudeHook(String payloadJson)
  {
    this(payloadJson, createTempDirs());
  }

  /**
   * Creates a new test hook scope from a bundle of temp dirs and the specified hook payload.
   *
   * @param payloadJson the hook input JSON string (must contain a {@code session_id} field)
   * @param bundle the bundle of temporary directory paths
   * @throws NullPointerException if {@code payloadJson} or {@code bundle} are null
   * @throws IllegalArgumentException if {@code payloadJson} is blank
   * @throws IllegalStateException if the JSON is malformed or missing required fields
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
  private TestClaudeHook(String payloadJson, TempDirBundle bundle)
  {
    this(parseJson(payloadJson, AbstractClaudeHook.createStdinMapper()), bundle.projectPath(), bundle.pluginRoot(),
      bundle.claudeConfigPath());
  }

  /**
   * Creates a new test hook scope from a bundle of temp dirs and a default empty hook payload.
   *
   * @param bundle the bundle of temporary directory paths
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
  private TestClaudeHook(TempDirBundle bundle)
  {
    this(defaultPayload(AbstractClaudeHook.createStdinMapper()), bundle.projectPath(), bundle.pluginRoot(),
      bundle.claudeConfigPath());
  }

  /**
   * Creates a new test hook scope with specified paths and a default empty hook payload.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   * @throws NullPointerException if any parameter is null
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
  public TestClaudeHook(Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    this(defaultPayload(AbstractClaudeHook.createStdinMapper()), projectPath, pluginRoot, claudeConfigPath,
      TerminalType.WINDOWS_TERMINAL);
  }

  /**
   * Creates a new test hook scope with a JSON payload string and specified paths.
   *
   * @param payloadJson the hook input JSON string (must contain a {@code session_id} field)
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code payloadJson} is blank
   * @throws IllegalStateException if the JSON is malformed or missing required fields
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
  public TestClaudeHook(String payloadJson, Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    this(parseJson(payloadJson, AbstractClaudeHook.createStdinMapper()), projectPath, pluginRoot, claudeConfigPath,
      TerminalType.WINDOWS_TERMINAL);
  }

  /**
   * Creates a new test hook scope with a pre-parsed JSON payload and specified paths.
   *
   * @param hookPayload the parsed hook JSON payload
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   * @throws NullPointerException if any parameter is null
   */
  public TestClaudeHook(JsonNode hookPayload, Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    this(hookPayload, projectPath, pluginRoot, claudeConfigPath, TerminalType.WINDOWS_TERMINAL);
  }

  /**
   * Creates a new test hook scope with a JSON payload string, specified paths, and a custom terminal type.
   *
   * @param payloadJson the hook input JSON string (must contain a {@code session_id} field)
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   * @param terminalType the terminal type to report
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code payloadJson} is blank
   * @throws IllegalStateException if the JSON is malformed or missing required fields
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
  public TestClaudeHook(String payloadJson, Path projectPath, Path pluginRoot, Path claudeConfigPath,
    TerminalType terminalType)
  {
    this(parseJson(payloadJson, AbstractClaudeHook.createStdinMapper()), projectPath, pluginRoot, claudeConfigPath,
      terminalType);
  }

  /**
   * Creates a new test hook scope with all parameters explicit.
   *
   * @param hookPayload the parsed hook JSON payload
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   * @param terminalType the terminal type to report
   * @throws NullPointerException if any parameter is null
   */
  public TestClaudeHook(JsonNode hookPayload, Path projectPath, Path pluginRoot, Path claudeConfigPath,
    TerminalType terminalType)
  {
    super(hookPayload, projectPath, pluginRoot, claudeConfigPath);
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(terminalType, "terminalType").isNotNull();
    this.terminalType = terminalType;
    this.workDir = projectPath;
  }

  /**
   * Creates a default hook payload node containing only the default session ID.
   *
   * @param mapper the JSON mapper to use
   * @return the default payload node
   */
  private static JsonNode defaultPayload(JsonMapper mapper)
  {
    String json = "{\"session_id\": \"" + DEFAULT_SESSION_ID + "\"}";
    return mapper.readTree(json);
  }

  /**
   * Parses a JSON string into a JsonNode.
   *
   * @param json the JSON string to parse
   * @param mapper the JSON mapper to use
   * @return the parsed node
   * @throws IllegalArgumentException if {@code json} is blank
   * @throws IllegalStateException if the JSON is malformed
   */
  private static JsonNode parseJson(String json, JsonMapper mapper)
  {
    requireThat(json, "json").isNotBlank();
    return readFrom(mapper, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Creates temporary directories for the no-arg constructor.
   *
   * @return a bundle containing the created paths
   */
  private static TempDirBundle createTempDirs()
  {
    try
    {
      Path projectPath = Files.createTempDirectory("test-hook-project");
      Path pluginRoot = Files.createTempDirectory("test-hook-plugin");
      Path configDir = Files.createTempDirectory("test-hook-config");
      return new TempDirBundle(projectPath, pluginRoot, configDir);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Holds the temporary directory paths for the no-arg constructor.
   *
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the config directory path
   */
  private record TempDirBundle(Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    /**
     * Creates a new bundle.
     *
     * @param projectPath the project directory path
     * @param pluginRoot the plugin root directory path
     * @param claudeConfigPath the config directory path
     * @throws NullPointerException if any parameter is null
     */
    TempDirBundle
    {
      requireThat(projectPath, "projectPath").isNotNull();
      requireThat(pluginRoot, "pluginRoot").isNotNull();
      requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    }
  }

  @Override
  public String getPluginPrefix()
  {
    ensureOpen();
    return "cat";
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return workDir;
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return terminalType;
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return "UTC";
  }
}
