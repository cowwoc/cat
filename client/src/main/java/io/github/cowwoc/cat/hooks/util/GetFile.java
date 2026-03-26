/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/**
 * Reads a file with per-agent deduplication.
 * <p>
 * On the first request for a given file path within an agent's session, returns the raw file content and
 * creates a marker file. On subsequent requests within the same agent session, returns a short reference
 * message directing the model to reuse the earlier Read result.
 * <p>
 * <b>Agent tracking:</b> Marker files are stored under
 * {@code {catWorkPath}/sessions/{catAgentId}/loaded/} using the URL-encoded file path as the marker
 * filename. Main agents use the session ID as their agent path; subagents use
 * {@code {sessionId}/subagents/{agentId}}.
 * <p>
 * <b>No preprocessor processing:</b> Returned content is raw file content — {@code !} preprocessor
 * directives in the file are not expanded.
 * <p>
 * <b>Usage:</b> {@code get-file <catAgentId> <file-path>}
 *
 * @see io.github.cowwoc.cat.hooks.session.ClearAgentMarkers
 */
public final class GetFile implements SkillOutput
{
  private final ClaudeTool scope;

  /**
   * Creates a new GetFile instance.
   *
   * @param scope the ClaudeTool for accessing shared services and environment paths
   * @throws NullPointerException if {@code scope} is null
   */
  public GetFile(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Returns the file content on first request, or a short reference on subsequent requests.
   *
   * @param args command-line arguments; {@code args[0]} must be the CAT agent ID and
   *   {@code args[1]} must be the file path to read
   * @return the file content (first load) or a short reference message (subsequent loads)
   * @throws IllegalArgumentException if {@code args} has fewer than 2 elements, if catAgentId or file path
   *   are blank, or if catAgentId causes path traversal outside the session base path
   * @throws IOException if the file cannot be read or the marker directory cannot be created
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length < 2 || args[0].isBlank())
    {
      throw new IllegalArgumentException(
        "catAgentId is required as the first argument but was not provided. " +
          "Usage: get-file <catAgentId> <file-path>");
    }
    if (args.length > 2)
    {
      throw new IllegalArgumentException(
        "Expected exactly 2 arguments (catAgentId, file-path), got " + args.length + ". " +
          "Usage: get-file <catAgentId> <file-path>");
    }
    if (args[1].isBlank())
    {
      throw new IllegalArgumentException(
        "File path is required as the second argument but was not provided. " +
          "Usage: get-file <catAgentId> <file-path>");
    }

    String catAgentId = args[0];
    String filePath = args[1];
    Path path = Paths.get(filePath);
    String fileName = path.getFileName().toString();

    // Resolve per-agent marker directory using catAgentId
    Path baseDir = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
    Path agentDir = GetSkill.resolveAndValidateContainment(baseDir, catAgentId, "catAgentId");

    // Marker file: URL-encoded path to avoid collisions between different file paths
    String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
    Path loadedDir = agentDir.resolve(GetSkill.LOADED_DIR);
    Path markerFile = loadedDir.resolve(encodedPath);

    if (Files.exists(markerFile))
    {
      return "see your earlier Read result for " + fileName;
    }

    if (!Files.exists(path))
    {
      throw new IOException(
        "File not found: " + filePath + ". " +
          "Ensure the path is correct and the file exists before referencing it.");
    }

    String content = Files.readString(path, StandardCharsets.UTF_8);
    Files.createDirectories(loadedDir);
    Files.writeString(markerFile, "", StandardCharsets.UTF_8);
    return content;
  }

  /**
   * Main method for command-line execution.
   *
   * @param args command-line arguments: catAgentId file-path
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetFile.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the get-file command, writing the result to the given output stream.
   *
   * @param scope the JVM scope
   * @param args  command-line arguments: catAgentId file-path
   * @param out   the output stream to write to
   * @throws NullPointerException if any of {@code scope}, {@code args}, or {@code out} are null
   */
  public static void run(ClaudeTool scope, String[] args, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    try
    {
      GetFile getFile = new GetFile(scope);
      String result = getFile.getOutput(args);
      out.print(result);
    }
    catch (IOException e)
    {
      Logger log = LoggerFactory.getLogger(GetFile.class);
      log.error("Error reading file", e);
      out.println(block(scope, e.getMessage()));
    }
  }
}
