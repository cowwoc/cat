package io.github.cowwoc.cat.hooks.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility class for running external processes and capturing output.
 * <p>
 * Provides methods for executing processes and reading their output
 * without triggering PMD's AssignmentInOperand warning.
 */
public final class ProcessRunner
{
  /**
   * Private constructor to prevent instantiation.
   */
  private ProcessRunner()
  {
  }

  /**
   * Runs a command and captures all output as a single string.
   *
   * @param command the command and arguments to run
   * @return the output trimmed, or null on error or non-zero exit code
   */
  public static String runAndCapture(List<String> command)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        readAllLines(reader, output);
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        return null;
      return output.toString();
    }
    catch (Exception _)
    {
      // Return null - process execution failures are expected in some contexts
      return null;
    }
  }

  /**
   * Runs a command and captures all output as a single string, ignoring exit code.
   *
   * @param command the command and arguments to run
   * @return the output trimmed, or empty string on error
   */
  public static String runAndCaptureIgnoreExit(List<String> command)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        readAllLines(reader, output);
      }

      process.waitFor();
      return output.toString();
    }
    catch (Exception _)
    {
      // Return empty string - process execution failures are expected in some contexts
      return "";
    }
  }

  /**
   * Reads all lines from a reader into a StringBuilder.
   *
   * @param reader the reader to read from
   * @param output the StringBuilder to append to
   * @throws Exception if reading fails
   */
  private static void readAllLines(BufferedReader reader, StringBuilder output) throws Exception
  {
    String line = reader.readLine();
    while (line != null)
    {
      if (output.length() > 0)
        output.append('\n');
      output.append(line);
      line = reader.readLine();
    }
  }
}
