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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Atomically creates the session directory and writes a marker file for a given session, issue, and content.
 * <p>
 * Replaces the 3-step bash pattern ({@code mkdir}, {@code git rev-parse}, bash redirect) with a single
 * CLI invocation, reducing round-trips and providing consistent error handling.
 */
public final class WriteSessionMarker implements SkillOutput
{
  private final ClaudeTool scope;

  /**
   * Creates a new WriteSessionMarker.
   *
   * @param scope the ClaudeTool providing access to the project path
   * @throws NullPointerException if {@code scope} is null
   */
  public WriteSessionMarker(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Creates the session directory and writes the marker file.
   * <p>
   * The marker file is written at:
   * {@code {catWorkPath}/sessions/{sessionId}/squash-complete-{issueId}}
   *
   * @param args exactly 3 arguments: {@code session-id}, {@code issue-id}, {@code marker-content}
   * @return empty string (the marker file is the side effect)
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if {@code args} does not contain exactly 3 elements, if
   *   {@code args[0]} (session-id) is blank, if {@code args[1]} (issue-id) is blank, or if the
   *   issue-id would escape the session directory
   * @throws IOException              if the directory or file cannot be created
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 3)
    {
      throw new IllegalArgumentException(
        "Expected exactly 3 arguments (session-id, issue-id, marker-content), got " + args.length + ". " +
          "Usage: write-session-marker <session-id> <issue-id> <marker-content>");
    }
    String sessionId = args[0];
    if (sessionId.isBlank())
    {
      throw new IllegalArgumentException(
        "session-id is required as the first argument but was blank. " +
          "Usage: write-session-marker <session-id> <issue-id> <marker-content>");
    }
    String issueId = args[1];
    if (issueId.isBlank())
    {
      throw new IllegalArgumentException(
        "issue-id is required as the second argument but was blank. " +
          "Usage: write-session-marker <session-id> <issue-id> <marker-content>");
    }
    String markerContent = args[2];
    requireThat(markerContent, "markerContent").isNotNull();

    Path baseDir = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
    Path sessionDir = GetSkill.resolveAndValidateContainment(baseDir, sessionId, "session-id");
    Path markerFile = GetSkill.resolveAndValidateContainment(sessionDir, "squash-complete-" + issueId,
      "issue-id");

    Files.createDirectories(sessionDir);
    Files.writeString(markerFile, markerContent, UTF_8);
    return "";
  }

  /**
   * Main entry point for command-line invocation.
   * <p>
   * Usage: {@code write-session-marker <session-id> <issue-id> <marker-content>}
   *
   * @param args command-line arguments: session-id, issue-id, marker-content
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(WriteSessionMarker.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the write-session-marker logic with caller-provided streams.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   *
   * @param scope the JVM scope
   * @param args  the command-line arguments: session-id, issue-id, marker-content
   * @param out   the output stream to write to
   * @throws NullPointerException if {@code args} or {@code out} are null
   * @throws IOException          if the marker file cannot be written
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String output = new WriteSessionMarker(scope).getOutput(args);
    if (!output.isEmpty())
      out.print(output);
  }
}
