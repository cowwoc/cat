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
 * Reads a named marker file from a session directory, printing its content to stdout.
 * <p>
 * Exits non-zero when the marker file is absent so callers can use the {@code || echo ""} pattern.
 */
public final class ReadSessionMarker implements SkillOutput
{
  private final ClaudeTool scope;

  /**
   * Creates a new ReadSessionMarker.
   *
   * @param scope the ClaudeTool providing access to the project path
   * @throws NullPointerException if {@code scope} is null
   */
  public ReadSessionMarker(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Reads the marker file and returns its content.
   * <p>
   * The marker file is read from:
   * {@code {catWorkPath}/sessions/{sessionId}/{markerName}}
   *
   * @param args exactly 2 arguments: {@code session-id}, {@code marker-name}
   * @return the content of the marker file
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if {@code args} does not contain exactly 2 elements, if
   *   {@code args[0]} (session-id) is blank, or if {@code args[1]} (marker-name) is blank
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
        "Expected exactly 2 arguments (session-id, marker-name), got " + args.length + ". " +
          "Usage: read-session-marker <session-id> <marker-name>");
    }
    String sessionId = args[0];
    if (sessionId.isBlank())
    {
      throw new IllegalArgumentException(
        "session-id is required as the first argument but was blank. " +
          "Usage: read-session-marker <session-id> <marker-name>");
    }
    String markerName = args[1];
    if (markerName.isBlank())
    {
      throw new IllegalArgumentException(
        "marker-name is required as the second argument but was blank. " +
          "Usage: read-session-marker <session-id> <marker-name>");
    }

    Path baseDir = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
    Path sessionDir = PathUtils.normalize(baseDir, sessionId, "session-id");
    Path markerFile = PathUtils.normalize(sessionDir, markerName, "marker-name");

    return Files.readString(markerFile, UTF_8);
  }

  /**
   * Main entry point for command-line invocation.
   * <p>
   * Usage: {@code read-session-marker <session-id> <marker-name>}
   * <p>
   * Exits with code 1 when the marker file is absent, so callers can use the {@code || echo ""} pattern.
   *
   * @param args command-line arguments: session-id, marker-name
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
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
   * @param scope the ClaudeTool scope
   * @param args  the command-line arguments: session-id, marker-name
   * @param out   the output stream to write to
   * @throws NullPointerException if {@code args} or {@code out} are null
   * @throws IOException          if the marker file cannot be read
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    String output = new ReadSessionMarker(scope).getOutput(args);
    if (!output.isEmpty())
      out.print(output);
  }
}
