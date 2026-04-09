/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import io.github.cowwoc.cat.claude.hook.skills.TerminalType;
import io.github.cowwoc.pouch10.core.ConcurrentLazyReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The main implementation of {@link ClaudeStatusline} for production use.
 * <p>
 * Reads and parses the statusline JSON from stdin at construction time. The project path is
 * extracted from the JSON's {@code workspace.project_dir} field, falling back to the
 * {@code CLAUDE_PROJECT_DIR} environment variable.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe.
 */
public final class MainClaudeStatusline extends AbstractClaudeStatusline
{
  private final ConcurrentLazyReference<TerminalType> terminalTypeRef =
    ConcurrentLazyReference.create(this::terminalType);
  private final ConcurrentLazyReference<String> tzRef =
    ConcurrentLazyReference.create(this::tz);

  /**
   * Creates a new production Claude statusline scope, reading statusline JSON from stdin.
   * <p>
   * Reads all bytes from {@code stdin}, extracts the project path from the JSON's
   * {@code workspace.project_dir} field (falling back to the {@code CLAUDE_PROJECT_DIR}
   * environment variable), then passes the bytes to the superclass for statusline field parsing.
   *
   * @param stdin the input stream providing Claude Code hook JSON (typically {@code System.in})
   * @throws NullPointerException if {@code stdin} is null
   * @throws IOException if an I/O error occurs while reading the stream
   */
  public MainClaudeStatusline(InputStream stdin) throws IOException
  {
    this(stdin.readAllBytes());
  }

  /**
   * Creates a new production Claude statusline scope from pre-read stdin bytes.
   *
   * @param stdinBytes the raw bytes from stdin
   * @throws IOException if an I/O error occurs
   */
  private MainClaudeStatusline(byte[] stdinBytes) throws IOException
  {
    super(extractProjectPath(stdinBytes), new ByteArrayInputStream(stdinBytes));
  }

  /**
   * Extracts the project path from the stdin JSON's {@code workspace.project_dir} field.
   * <p>
   * Falls back to the {@code CLAUDE_PROJECT_DIR} environment variable if the JSON field is absent.
   *
   * @param stdinBytes the raw bytes from stdin
   * @return the project path
   * @throws AssertionError if neither the JSON field nor the environment variable provides a project path
   */
  private static Path extractProjectPath(byte[] stdinBytes)
  {
    try
    {
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode root = mapper.readTree(new String(stdinBytes, StandardCharsets.UTF_8));
      JsonNode workspaceNode = root.get("workspace");
      if (workspaceNode != null && !workspaceNode.isNull())
      {
        JsonNode projectDirNode = workspaceNode.get("project_dir");
        if (projectDirNode != null && projectDirNode.isString())
        {
          String projectDir = projectDirNode.asString();
          if (!projectDir.isBlank())
            return Path.of(projectDir);
        }
      }
    }
    catch (JacksonException _)
    {
      // Fall through to env var fallback
    }
    String envValue = System.getenv("CLAUDE_PROJECT_DIR");
    if (envValue == null || envValue.isBlank())
      throw new AssertionError("Neither workspace.project_dir in stdin JSON nor CLAUDE_PROJECT_DIR is set");
    return Path.of(envValue);
  }

  /**
   * Detects the terminal type.
   *
   * @return the detected terminal type
   */
  private TerminalType terminalType()
  {
    return TerminalType.detect();
  }

  /**
   * Reads the timezone from the environment or defaults to UTC.
   *
   * @return the timezone string
   */
  private String tz()
  {
    String tzValue = System.getenv("TZ");
    if (tzValue == null || tzValue.isBlank())
      return "UTC";
    return tzValue;
  }

  @Override
  public Path getWorkDir()
  {
    ensureOpen();
    return Path.of(System.getProperty("user.dir"));
  }

  @Override
  public TerminalType getTerminalType()
  {
    ensureOpen();
    return terminalTypeRef.getValue();
  }

  @Override
  public String getTimezone()
  {
    ensureOpen();
    return tzRef.getValue();
  }
}
