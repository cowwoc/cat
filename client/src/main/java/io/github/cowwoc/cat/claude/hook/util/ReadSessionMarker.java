/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.util;

import static io.github.cowwoc.cat.claude.hook.Strings.block;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;
import io.github.cowwoc.cat.claude.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reads the marker file from a worktree's markers directory, printing its content to stdout.
 * <p>
 * Exits non-zero when the marker file is absent so callers can use the {@code || echo ""} pattern.
 */
public final class ReadSessionMarker implements SkillOutput
{
  /**
   * Creates a new ReadSessionMarker.
   */
  public ReadSessionMarker()
  {
  }

  /**
   * Reads the marker file and returns its content.
   * <p>
   * The marker file is read from:
   * {@code {worktreePath}/.cat/work/markers/{issueId}}
   *
   * @param args exactly 2 arguments: {@code worktree-path}, {@code issue-id}
   * @return the content of the marker file
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if {@code args} does not contain exactly 2 elements, if
   *   {@code args[0]} (worktree-path) is blank, or if {@code args[1]} (issue-id) is blank
   * @throws NoSuchFileException      if the marker file does not exist
   * @throws IOException              if the marker file cannot be read
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 2)
    {
      throw new IllegalArgumentException(
        "Expected exactly 2 arguments (worktree-path, issue-id), got " + args.length + ". " +
          "Usage: read-session-marker <worktree-path> <issue-id>");
    }
    String worktreePathStr = args[0];
    if (worktreePathStr.isBlank())
    {
      throw new IllegalArgumentException(
        "worktree-path is required as the first argument but was blank. " +
          "Usage: read-session-marker <worktree-path> <issue-id>");
    }
    String issueId = args[1];
    if (issueId.isBlank())
    {
      throw new IllegalArgumentException(
        "issue-id is required as the second argument but was blank. " +
          "Usage: read-session-marker <worktree-path> <issue-id>");
    }

    Path worktreePath = Path.of(worktreePathStr).toAbsolutePath().normalize();
    Path markersDir = worktreePath.resolve(".cat/work/markers").toAbsolutePath().normalize();
    Path markerFile = PathUtils.normalize(markersDir, issueId, "issue-id");

    return Files.readString(markerFile, UTF_8);
  }

  /**
   * Main entry point for command-line invocation.
   * <p>
   * Usage: {@code read-session-marker <worktree-path> <issue-id>}
   * <p>
   * Exits with code 1 when the marker file is absent, so callers can use the {@code || echo ""} pattern.
   *
   * @param args command-line arguments: worktree-path, issue-id
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(args, System.out);
      }
      catch (NoSuchFileException e)
      {
        // File absent — exit 1 so callers can use the `|| echo ""` pattern.
        // Print to stderr; the skill suppresses it with 2>/dev/null.
        System.err.println("Marker file not found: " + e.getFile());
        System.exit(1);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(ReadSessionMarker.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the read-session-marker logic with caller-provided streams.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   *
   * @param args the command-line arguments: worktree-path, issue-id
   * @param out  the output stream to write to
   * @throws NullPointerException if {@code args} or {@code out} are null
   * @throws IOException          if the marker file cannot be read
   */
  public static void run(String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String output = new ReadSessionMarker().getOutput(args);
    if (!output.isEmpty())
      out.print(output);
  }
}
