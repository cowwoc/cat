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
import java.nio.file.Path;
import java.util.Optional;

/**
 * Restores the agent's working directory and deferred tool state after context compaction.
 * <p>
 * When a session is compacted ({@code source} is {@code "compact"}), this handler:
 * <ol>
 *   <li>Injects an instruction to batch-load all commonly-needed deferred tools in a single
 *       ToolSearch call, avoiding the 7-10 sequential round-trips that occur without this
 *       instruction.</li>
 *   <li>Reads the session's CWD file from the session CAT work directory
 *       ({@code {claudeProjectDir}/.cat/work/verify/{sessionId}/session.cwd}) written
 *       by {@link PreCompactHook}. If the file exists and the recorded path is still a live
 *       directory, additional context is injected instructing the agent to {@code cd} back into that
 *       path.</li>
 * </ol>
 */
public final class RestoreCwdAfterCompaction implements SessionStartHandler
{
  public static final String BATCH_TOOLSEARCH_INSTRUCTION = """
    After context compaction, batch-load all commonly-needed deferred tools in a single \
    ToolSearch call using comma-separated select syntax: \
    `select:Bash,Read,Edit,Grep,Write,Agent,Skill,AskUserQuestion`""";
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

    Optional<String> cdInstruction = resolveCdInstruction();
    String context = cdInstruction.
      map(cd -> cd + "\n\n" + BATCH_TOOLSEARCH_INSTRUCTION).
      orElse(BATCH_TOOLSEARCH_INSTRUCTION);
    return Result.context(context);
  }

  private Optional<String> resolveCdInstruction()
  {
    Path cwdFile = scope.getSessionCatDir().resolve("session.cwd");
    if (Files.notExists(cwdFile))
      return Optional.empty();

    String savedPath;
    try
    {
      savedPath = Files.readString(cwdFile).strip();
    }
    catch (IOException e)
    {
      log.debug("Failed to read .cwd file {}: {}", cwdFile, e.getMessage());
      return Optional.empty();
    }

    if (savedPath.isBlank())
      return Optional.empty();

    Path savedDir = Path.of(savedPath);
    if (!Files.isDirectory(savedDir))
      return Optional.empty();

    String instruction = """
      Your context was compacted. Your previous working directory was: %s

      Run `cd %s` once now, then continue working from that directory.""".
      formatted(savedPath, savedPath);
    return Optional.of(instruction);
  }
}
