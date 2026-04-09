/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Extracts all Turn sections from a test-case markdown file, excluding assertions.
 * <p>
 * Reads a markdown file and outputs content from the first {@code ## Turn} heading
 * through all subsequent {@code ## Turn N} sections, stopping before {@code ## Assertions}.
 * This ensures test-run agents receive the full multi-turn prompt without assertion content.
 */
public final class ExtractTurnsContent
{
  /**
   * Prevents instantiation.
   */
  private ExtractTurnsContent()
  {
  }

  /**
   * Extracts individual Turn sections from the given markdown lines.
   * <p>
   * Each element in the returned list corresponds to one {@code ## Turn N} section's content
   * (without the heading line itself). Stops at {@code ## Assertions} or end of file.
   *
   * @param lines the lines of the markdown file
   * @return a list of turn contents, one per {@code ## Turn N} section, or empty list if no turns found
   * @throws NullPointerException if {@code lines} is null
   */
  public static List<String> extractTurns(List<String> lines)
  {
    requireThat(lines, "lines").isNotNull();
    List<String> turns = new ArrayList<>();
    StringJoiner currentTurn = null;
    boolean insideCodeBlock = false;
    for (String line : lines)
    {
      if (line.startsWith("```"))
        insideCodeBlock = !insideCodeBlock;
      if (!insideCodeBlock && line.startsWith("## Turn "))
      {
        if (currentTurn != null)
          turns.add(currentTurn.toString());
        currentTurn = new StringJoiner("\n");
        continue;
      }
      if (!insideCodeBlock && currentTurn != null && line.equals("## Assertions"))
      {
        turns.add(currentTurn.toString());
        currentTurn = null;
        break;
      }
      if (currentTurn != null)
        currentTurn.add(line);
    }
    if (currentTurn != null)
      turns.add(currentTurn.toString());
    return turns;
  }

  /**
   * Reads Turn sections from the input file, writes each turn to a separate output file,
   * and prints the output file paths to stdout (one per line).
   * <p>
   * Output files are named by inserting {@code _turnN} before the extension of the output path.
   * For example, if {@code outputBase} is {@code /tmp/TC1.md}, the files created are
   * {@code /tmp/TC1_turn1.md}, {@code /tmp/TC1_turn2.md}, etc.
   *
   * @param inputFile  the path to the markdown file containing Turn sections
   * @param outputBase the base path for output files (turn number inserted before extension)
   * @param out        the output stream to print the created file paths to
   * @throws IOException          if the input file cannot be read or output files cannot be written
   * @throws NullPointerException if {@code inputFile}, {@code outputBase}, or {@code out} are null
   */
  public static void run(Path inputFile, Path outputBase, PrintStream out) throws IOException
  {
    requireThat(inputFile, "inputFile").isNotNull();
    requireThat(outputBase, "outputBase").isNotNull();
    requireThat(out, "out").isNotNull();
    List<String> lines = Files.readAllLines(inputFile);
    List<String> turns = extractTurns(lines);
    String baseName = outputBase.getFileName().toString();
    int dotIndex = baseName.lastIndexOf('.');
    String nameWithoutExt;
    String ext;
    if (dotIndex >= 0)
    {
      nameWithoutExt = baseName.substring(0, dotIndex);
      ext = baseName.substring(dotIndex);
    }
    else
    {
      nameWithoutExt = baseName;
      ext = "";
    }
    Path parentDir = outputBase.getParent();
    for (int i = 0; i < turns.size(); ++i)
    {
      Path turnFile = parentDir.resolve(nameWithoutExt + "_turn" + (i + 1) + ext);
      Files.writeString(turnFile, turns.get(i));
      out.println(turnFile);
    }
  }

  /**
   * CLI entry point for the extract-turns tool.
   *
   * @param args two arguments: the input file path and the output file path
   */
  public static void main(String[] args)
  {
    if (args.length != 2)
    {
      System.err.println("Usage: extract-turns <input-file> <output-base-path>");
      System.exit(1);
    }
    try
    {
      run(Path.of(args[0]), Path.of(args[1]), System.out);
    }
    catch (IOException e)
    {
      Logger log = LoggerFactory.getLogger(ExtractTurnsContent.class);
      log.error("", e);
      System.err.println("ERROR: " + e.getMessage());
      System.exit(1);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(ExtractTurnsContent.class);
      log.error("Unexpected error", e);
      System.err.println("ERROR: " +
        Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
      System.exit(1);
    }
  }
}
