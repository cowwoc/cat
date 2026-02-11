package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.BufferedReader;
import java.io.IOException;
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
   * Result of running a process.
   *
   * @param exitCode the process exit code
   * @param stdout the standard output
   */
  public record Result(int exitCode, String stdout)
  {
    /**
     * Creates a new process result.
     *
     * @param exitCode the process exit code
     * @param stdout the standard output
     * @throws NullPointerException if {@code stdout} is null
     */
    public Result
    {
      requireThat(stdout, "stdout").isNotNull();
    }
  }

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
    catch (IOException | InterruptedException _)
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
    catch (IOException | InterruptedException _)
    {
      // Return empty string - process execution failures are expected in some contexts
      return "";
    }
  }

  /**
   * Runs a command and returns the exit code and stdout.
   *
   * @param command the command and arguments to run
   * @return the result with exit code and stdout
   */
  public static Result run(String... command)
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
      return new Result(exitCode, output.toString());
    }
    catch (IOException | InterruptedException _)
    {
      return new Result(1, "");
    }
  }

  /**
   * Reads all lines from a reader into a StringBuilder.
   *
   * @param reader the reader to read from
   * @param output the StringBuilder to append to
   * @throws IOException if reading fails
   */
  private static void readAllLines(BufferedReader reader, StringBuilder output) throws IOException
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
