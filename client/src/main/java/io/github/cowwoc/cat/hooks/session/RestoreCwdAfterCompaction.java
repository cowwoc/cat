/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.session;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.PreCompactHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Restores the agent's working directory after context compaction.
 * <p>
 * When a session is compacted ({@code source} is {@code "compact"}), this handler reads the
 * session's {@code .claude/cat/sessions/{session_id}.cwd} file written by
 * {@link PreCompactHook}. If the file exists and the recorded path is still a live directory,
 * additional context is injected instructing the agent to {@code cd} back into that path.
 */
public final class RestoreCwdAfterCompaction implements SessionStartHandler
{
  private final Logger log = LoggerFactory.getLogger(RestoreCwdAfterCompaction.class);
  private final JvmScope scope;

  /**
   * Creates a new RestoreCwdAfterCompaction handler.
   *
   * @param scope the JVM scope providing environment paths
   * @throws NullPointerException if {@code scope} is null
   */
  public RestoreCwdAfterCompaction(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result handle(HookInput input)
  {
    requireThat(input, "input").isNotNull();

    String source = input.getString("source");
    if (!source.equals("compact"))
      return Result.empty();

    String sessionId = input.getSessionId();
    if (sessionId.isBlank())
      return Result.empty();

    Path projectDir = scope.getClaudeProjectDir();
    Path cwdFile = projectDir.resolve(".claude/cat/sessions/" + sessionId + ".cwd");

    if (!Files.exists(cwdFile))
      return Result.empty();

    String savedPath;
    try
    {
      savedPath = Files.readString(cwdFile).strip();
    }
    catch (IOException e)
    {
      log.debug("Failed to read .cwd file {}: {}", cwdFile, e.getMessage());
      return Result.empty();
    }

    if (savedPath.isBlank())
      return Result.empty();

    Path savedDir = Path.of(savedPath);
    if (!Files.isDirectory(savedDir, LinkOption.NOFOLLOW_LINKS))
      return Result.empty();

    String context = """
      Your context was compacted. Your previous working directory was: %s

      Run `cd %s` once now, then continue working from that directory.\
      """.formatted(savedPath, savedPath);
    return Result.context(context);
  }
}
