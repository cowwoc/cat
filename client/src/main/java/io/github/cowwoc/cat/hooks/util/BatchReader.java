/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch file search and read utility.
 * <p>
 * Finds files matching a grep pattern and reads them in one operation,
 * reducing round-trips. Equivalent to batch-read.sh functionality.
 */
public final class BatchReader
{
  /**
   * Configuration for batch reading.
   *
   * @param pattern the grep pattern to search for
   * @param maxFiles maximum number of files to read
   * @param contextLines lines to include per file (0 = all)
   * @param fileType file type filter (e.g., "java", "sh", "md"), or empty string for no filter
   */
  public record Config(String pattern, int maxFiles, int contextLines, String fileType)
  {
    /**
     * Creates a new batch reader configuration.
     *
     * @param pattern the grep pattern to search for
     * @param maxFiles maximum number of files to read
     * @param contextLines lines to include per file (0 = all)
     * @param fileType file type filter (e.g., "java", "sh", "md"), or empty string for no filter
     * @throws NullPointerException if {@code pattern} or {@code fileType} is null
     * @throws IllegalArgumentException if {@code maxFiles} is not positive, or {@code contextLines} is negative
     */
    public Config
    {
      requireThat(pattern, "pattern").isNotNull();
      requireThat(maxFiles, "maxFiles").isPositive();
      requireThat(contextLines, "contextLines").isNotNegative();
      requireThat(fileType, "fileType").isNotNull();
    }
  }

  /**
   * Result of batch reading operation.
   *
   * @param status the operation status
   * @param message result message
   * @param durationSeconds execution duration in seconds
   * @param pattern the search pattern used
   * @param filesFound number of files found
   * @param filesRead number of files actually read
   * @param outputContent the file contents (empty string on error)
   * @param workingDirectory the working directory
   * @param timestamp ISO-8601 timestamp
   */
  public record Result(
    OperationStatus status,
    String message,
    long durationSeconds,
    String pattern,
    int filesFound,
    int filesRead,
    String outputContent,
    String workingDirectory,
    String timestamp)
  {
    /**
     * Creates a new batch reader result.
     *
     * @param status the operation status
     * @param message result message
     * @param durationSeconds execution duration in seconds
     * @param pattern the search pattern used
     * @param filesFound number of files found
     * @param filesRead number of files actually read
     * @param outputContent the file contents (empty string on error)
     * @param workingDirectory the working directory
     * @param timestamp ISO-8601 timestamp
     * @throws NullPointerException if {@code status} or any string parameter is null
     * @throws IllegalArgumentException if {@code durationSeconds}, {@code filesFound}, or {@code filesRead}
     *   is negative
     */
    public Result
    {
      requireThat(status, "status").isNotNull();
      requireThat(message, "message").isNotNull();
      requireThat(durationSeconds, "durationSeconds").isNotNegative();
      requireThat(pattern, "pattern").isNotNull();
      requireThat(filesFound, "filesFound").isNotNegative();
      requireThat(filesRead, "filesRead").isNotNegative();
      requireThat(outputContent, "outputContent").isNotNull();
      requireThat(workingDirectory, "workingDirectory").isNotNull();
      requireThat(timestamp, "timestamp").isNotNull();
    }

    /**
     * Converts this result to JSON format.
     *
     * @param mapper the JSON mapper to use
     * @return JSON string representation
     * @throws NullPointerException if {@code mapper} is null
     * @throws IOException if JSON conversion fails
     */
    public String toJson(JsonMapper mapper) throws IOException
    {
      requireThat(mapper, "mapper").isNotNull();
      ObjectNode root = mapper.createObjectNode();
      root.put("status", status.toJson());
      root.put("message", message);
      root.put("duration_seconds", durationSeconds);
      root.put("pattern", pattern);
      root.put("files_found", filesFound);
      root.put("files_read", filesRead);
      root.put("working_directory", workingDirectory);
      root.put("timestamp", timestamp);
      return mapper.writeValueAsString(root);
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private BatchReader()
  {
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage: {@code batch-read <pattern> [--max-files N] [--context-lines N] [--file-type TYPE]}
   *
   * @param args command-line arguments
   * @throws IOException if file operations fail
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length < 1)
    {
      System.err.println("""
        {
          "status": "ERROR",
          "message": "Usage: batch-read <pattern> [--max-files N] [--context-lines N] [--file-type TYPE]"
        }""");
      System.exit(1);
    }

    String pattern = args[0];
    int maxFiles = 10;
    int contextLines = 0;
    String fileType = "";

    // Loop bound is args.length - 1 so that args[i+1] (the flag value) is always available.
    // A lone flag key at the last position is intentionally skipped to avoid ArrayIndexOutOfBoundsException.
    for (int i = 1; i < args.length - 1; ++i)
    {
      switch (args[i])
      {
        case "--max-files" ->
        {
          ++i;
          try
          {
            maxFiles = parseIntFlag("--max-files", args[i]);
          }
          catch (IllegalArgumentException e)
          {
            System.err.println(e.getMessage());
            System.exit(1);
          }
        }
        case "--context-lines" ->
        {
          ++i;
          try
          {
            contextLines = parseIntFlag("--context-lines", args[i]);
          }
          catch (IllegalArgumentException e)
          {
            System.err.println(e.getMessage());
            System.exit(1);
          }
        }
        case "--file-type" ->
        {
          ++i;
          fileType = args[i];
        }
        default ->
        {
          // ignore unknown flags
        }
      }
    }

    Config config = new Config(pattern, maxFiles, contextLines, fileType);
    Result result = read(config);

    // Print file contents to stdout for consumption
    if (result.status() == OperationStatus.SUCCESS)
      System.out.print(result.outputContent());

    // Print JSON metadata to stderr for diagnostics
    try (MainJvmScope scope = new MainJvmScope())
    {
      System.err.println(result.toJson(scope.getJsonMapper()));
    }
    catch (RuntimeException | AssertionError e)
    {
      System.err.println("{\"status\": \"ERROR\", \"message\": \"" +
        e.getMessage().replace("\"", "\\\"") + "\"}");
    }

    if (result.status() != OperationStatus.SUCCESS)
      System.exit(1);
  }

  /**
   * Reads files matching the specified pattern.
   *
   * @param config the batch reader configuration
   * @return the result including file contents and metadata
   * @throws NullPointerException if {@code config} is null
   * @throws IOException if file operations fail
   */
  public static Result read(Config config) throws IOException
  {
    requireThat(config, "config").isNotNull();

    long startTime = System.currentTimeMillis();
    String workingDir = Paths.get("").toAbsolutePath().toString();
    String timestamp = Instant.now().toString();

    try
    {
      List<String> matchingFiles = findMatchingFiles(config);

      if (matchingFiles.isEmpty())
      {
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        return new Result(
          OperationStatus.ERROR,
          "No files found matching pattern: " + config.pattern(),
          duration,
          config.pattern(),
          0,
          0,
          "",
          workingDir,
          timestamp);
      }

      int filesFound = matchingFiles.size();
      if (filesFound > config.maxFiles())
      {
        matchingFiles = matchingFiles.subList(0, config.maxFiles());
      }

      StringBuilder output = new StringBuilder(512);
      int filesRead = 0;

      for (String filePath : matchingFiles)
      {
        Path file = Paths.get(filePath);
        if (!Files.exists(file) || !Files.isRegularFile(file))
          continue;

        ++filesRead;

        output.append('\n').
          append("═══════════════════════════════════════════════════════════\n").
          append("FILE: ").append(filePath).append('\n').
          append("═══════════════════════════════════════════════════════════\n").
          append('\n');

        if (config.contextLines() == 0)
        {
          String content = Files.readString(file, StandardCharsets.UTF_8);
          output.append(content);
        }
        else
        {
          List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
          int totalLines = lines.size();
          int linesToRead = Math.min(config.contextLines(), totalLines);

          for (int i = 0; i < linesToRead; ++i)
          {
            output.append(String.format("%6d\t%s\n", i + 1, lines.get(i)));
          }

          if (totalLines > config.contextLines())
          {
            output.append('\n').
              append("[... truncated: showing ").append(config.contextLines()).
              append(" of ").append(totalLines).append(" lines ...]\n");
          }
        }

        output.append('\n').
          append("───────────────────────────────────────────────────────────\n").
          append('\n');
      }

      if (filesRead == 0)
      {
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        return new Result(
          OperationStatus.ERROR,
          "No content read from files",
          duration,
          config.pattern(),
          filesFound,
          0,
          "",
          workingDir,
          timestamp);
      }

      long duration = (System.currentTimeMillis() - startTime) / 1000;
      return new Result(
        OperationStatus.SUCCESS,
        "Successfully read " + filesRead + " file(s) matching pattern",
        duration,
        config.pattern(),
        filesFound,
        filesRead,
        output.toString(),
        workingDir,
        timestamp);
    }
    catch (IOException e)
    {
      long duration = (System.currentTimeMillis() - startTime) / 1000;
      return new Result(
        OperationStatus.ERROR,
        "Error: " + e.getMessage(),
        duration,
        config.pattern(),
        0,
        0,
        "",
        workingDir,
        timestamp);
    }
  }

  /**
   * Finds files matching the specified pattern using grep.
   *
   * @param config the batch reader configuration
   * @return list of matching file paths
   * @throws IOException if grep command fails
   */
  private static List<String> findMatchingFiles(Config config) throws IOException
  {
    List<String> command = new ArrayList<>();
    command.add("grep");
    command.add("-r");
    command.add("-l");

    if (!config.fileType().isEmpty())
    {
      command.add("--include=*." + config.fileType());
    }

    command.add("--");
    command.add(config.pattern());
    command.add(".");

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process process = pb.start();

    List<String> files = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      String line = reader.readLine();
      int count = 0;
      while (line != null && count < config.maxFiles())
      {
        if (!line.isBlank())
        {
          files.add(line.strip());
          ++count;
        }
        line = reader.readLine();
      }
    }

    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for grep", e);
    }

    if (exitCode != 0 && files.isEmpty())
    {
      return List.of();
    }

    return files;
  }

  /**
   * Parses an integer value for a command-line flag.
   *
   * @param flagName the name of the flag (e.g., "--max-files")
   * @param value the string value to parse
   * @return the parsed integer
   * @throws IllegalArgumentException if {@code value} is not a valid integer
   */
  public static int parseIntFlag(String flagName, String value)
  {
    try
    {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException _)
    {
      throw new IllegalArgumentException("Error: " + flagName + " requires an integer, got: " + value);
    }
  }
}
