/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.ExtractTurnsContent;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tests for {@link ExtractTurnsContent}.
 */
public class ExtractTurnsContentTest
{
  /**
   * Verifies that extraction returns Turn content and excludes assertion content
   * when the file contains both sections.
   */
  @Test
  public void extractsTurnContentOnly()
  {
    List<String> lines = List.of(
      "## Turn 1",
      "Ask the agent to refactor the login module.",
      "Provide the path src/login.js as context.",
      "## Assertions",
      "- Agent must not delete existing tests",
      "- Agent must preserve the public API");

    List<String> turns = ExtractTurnsContent.extractTurns(lines);
    requireThat(turns, "turns").size().isEqualTo(1);
    String turn1 = turns.get(0);
    requireThat(turn1, "turn1").contains("Ask the agent to refactor the login module.");
    requireThat(turn1, "turn1").contains("Provide the path src/login.js as context.");
    requireThat(turn1, "turn1").doesNotContain("Assertions");
    requireThat(turn1, "turn1").doesNotContain("Agent must not delete existing tests");
  }

  /**
   * Verifies that extraction correctly handles Turn 1 as the last section in the file
   * (no subsequent heading).
   */
  @Test
  public void extractsTurnAsLastSection()
  {
    List<String> lines = List.of(
      "## Turn 1",
      "Run the test suite and report failures.",
      "Include stack traces in the output.");

    List<String> turns = ExtractTurnsContent.extractTurns(lines);
    requireThat(turns, "turns").size().isEqualTo(1);
    String turn1 = turns.get(0);
    requireThat(turn1, "turn1").contains("Run the test suite and report failures.");
    requireThat(turn1, "turn1").contains("Include stack traces in the output.");
  }

  /**
   * Verifies that content from sections before Turn 1 is excluded from the extraction.
   */
  @Test
  public void excludesContentBeforeTurn1()
  {
    List<String> lines = List.of(
      "## Description",
      "This test validates the refactor skill.",
      "## Turn 1",
      "Refactor the database module.",
      "## Assertions",
      "- Must preserve schema");

    List<String> turns = ExtractTurnsContent.extractTurns(lines);
    requireThat(turns, "turns").size().isEqualTo(1);
    String turn1 = turns.get(0);
    requireThat(turn1, "turn1").contains("Refactor the database module.");
    requireThat(turn1, "turn1").doesNotContain("This test validates the refactor skill.");
    requireThat(turn1, "turn1").doesNotContain("Must preserve schema");
  }

  /**
   * Verifies that extraction returns separate entries for each Turn section in a multi-turn test case.
   */
  @Test
  public void extractsAllTurns()
  {
    List<String> lines = List.of(
      "## Turn 1",
      "The user says: \"Squash my last 3 commits.\"",
      "",
      "## Turn 2",
      "The user says: \"Actually, make it 5 commits.\"",
      "",
      "## Turn 3",
      "The user says: \"Ok go ahead.\"",
      "",
      "## Assertions",
      "1. The agent invokes git-squash.",
      "2. The agent updates the range.");

    List<String> turns = ExtractTurnsContent.extractTurns(lines);
    requireThat(turns, "turns").size().isEqualTo(3);
    requireThat(turns.get(0), "turn1").contains("Squash my last 3 commits.");
    requireThat(turns.get(1), "turn2").contains("Actually, make it 5 commits.");
    requireThat(turns.get(2), "turn3").contains("Ok go ahead.");
    for (String turn : turns)
    {
      requireThat(turn, "turn").doesNotContain("Assertions");
      requireThat(turn, "turn").doesNotContain("invokes git-squash");
    }
  }

  /**
   * Verifies that run() reads from an input file, extracts a single Turn section, and writes it
   * to a separate output file with the turn number appended before the extension.
   */
  @Test
  public void runWritesSingleTurnFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("extract-turns-test-");
    try
    {
      Path inputFile = tempDir.resolve("TC1.md");
      Files.writeString(inputFile, """
        ## Description
        This test validates the refactor skill.
        ## Turn 1
        Ask the agent to refactor the login module.
        ## Assertions
        - Agent must not delete existing tests
        """);

      Path outputBase = tempDir.resolve("TC1.md");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, true, UTF_8);

      ExtractTurnsContent.run(inputFile, outputBase, out);

      Path expectedFile = tempDir.resolve("TC1_turn1.md");
      requireThat(Files.exists(expectedFile), "turn1FileExists").isTrue();
      String turn1Content = Files.readString(expectedFile);
      requireThat(turn1Content, "turn1Content").contains("Ask the agent to refactor the login module.");
      requireThat(turn1Content, "turn1Content").doesNotContain("Assertions");

      String stdoutContent = baos.toString(UTF_8);
      requireThat(stdoutContent, "stdoutContent").contains("TC1_turn1.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() creates separate files for each turn in a multi-turn test case.
   */
  @Test
  public void runWritesMultipleTurnFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("extract-turns-test-");
    try
    {
      Path inputFile = tempDir.resolve("TC1.md");
      Files.writeString(inputFile, """
        ## Turn 1
        First turn prompt.
        ## Turn 2
        Second turn prompt.
        ## Turn 3
        Third turn prompt.
        ## Assertions
        1. Some assertion.
        """);

      Path outputBase = tempDir.resolve("TC1.md");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(baos, true, UTF_8);
      ExtractTurnsContent.run(inputFile, outputBase, out);

      Path turn1 = tempDir.resolve("TC1_turn1.md");
      Path turn2 = tempDir.resolve("TC1_turn2.md");
      Path turn3 = tempDir.resolve("TC1_turn3.md");
      requireThat(Files.exists(turn1), "turn1Exists").isTrue();
      requireThat(Files.exists(turn2), "turn2Exists").isTrue();
      requireThat(Files.exists(turn3), "turn3Exists").isTrue();

      requireThat(Files.readString(turn1), "turn1").contains("First turn prompt.");
      requireThat(Files.readString(turn2), "turn2").contains("Second turn prompt.");
      requireThat(Files.readString(turn3), "turn3").contains("Third turn prompt.");

      requireThat(Files.readString(turn1), "turn1").doesNotContain("assertion");
      requireThat(Files.readString(turn2), "turn2").doesNotContain("assertion");
      requireThat(Files.readString(turn3), "turn3").doesNotContain("assertion");

      String stdoutContent = baos.toString(UTF_8);
      requireThat(stdoutContent, "stdout").contains("TC1_turn1.md");
      requireThat(stdoutContent, "stdout").contains("TC1_turn2.md");
      requireThat(stdoutContent, "stdout").contains("TC1_turn3.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
