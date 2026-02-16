/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.bash.ComputeBoxLines;
import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ComputeBoxLines functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class ComputeBoxLinesTest
{
  /**
   * Verifies that commands without BOX_COMPUTE marker are allowed through.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void commandWithoutMarkerAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      BashHandler.Result result = handler.check("echo hello", "", null, null, "session1");
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with single line produces valid box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleLineBox() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nHello World";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains("╭").contains("╯").contains("Hello World");

      String[] lines = message.split("\n");
      int boxStartIndex = findBoxStartIndex(lines);
      requireThat(boxStartIndex, "boxStartIndex").isGreaterThanOrEqualTo(0);

      String topBorder = lines[boxStartIndex];
      String contentLine = lines[boxStartIndex + 1];
      String bottomBorder = lines[boxStartIndex + 2];

      requireThat(topBorder, "topBorder").startsWith("╭").endsWith("╮");
      requireThat(contentLine, "contentLine").isEqualTo("│ Hello World │");
      requireThat(bottomBorder, "bottomBorder").startsWith("╰").endsWith("╯");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with multiple lines produces valid box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multiLineBox() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nLine 1\nLine 2\nLine 3";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains("Line 1").contains("Line 2").contains("Line 3");

      String[] lines = message.split("\n");
      int boxStartIndex = findBoxStartIndex(lines);
      requireThat(boxStartIndex, "boxStartIndex").isGreaterThanOrEqualTo(0);

      String topBorder = lines[boxStartIndex];
      requireThat(topBorder, "topBorder").startsWith("╭").endsWith("╮");

      String bottomBorder = lines[boxStartIndex + 4];
      requireThat(bottomBorder, "bottomBorder").startsWith("╰").endsWith("╯");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with emoji content produces valid box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emojiContentBox() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\n📊 Overall: 91%\n🏆 112/122 tasks";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains("📊 Overall: 91%").contains("🏆 112/122 tasks");

      String[] lines = message.split("\n");
      int boxStartIndex = findBoxStartIndex(lines);
      requireThat(boxStartIndex, "boxStartIndex").isGreaterThanOrEqualTo(0);

      String topBorder = lines[boxStartIndex];
      String line1 = lines[boxStartIndex + 1];
      String line2 = lines[boxStartIndex + 2];
      String bottomBorder = lines[boxStartIndex + 3];

      requireThat(topBorder, "topBorder").startsWith("╭").endsWith("╮");
      requireThat(line1, "line1").startsWith("│").endsWith("│");
      requireThat(line2, "line2").startsWith("│").endsWith("│");
      requireThat(bottomBorder, "bottomBorder").startsWith("╰").endsWith("╯");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with empty content produces error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyContentProducesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains("No content items provided");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with varying content widths produces aligned box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void varyingWidthsProduceAlignedBox() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nShort\nMedium length\nVery long content line here";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();

      String[] lines = message.split("\n");
      int boxStartIndex = findBoxStartIndex(lines);
      requireThat(boxStartIndex, "boxStartIndex").isGreaterThanOrEqualTo(0);

      String topBorder = lines[boxStartIndex];
      String line1 = lines[boxStartIndex + 1];
      String line2 = lines[boxStartIndex + 2];
      String line3 = lines[boxStartIndex + 3];
      String bottomBorder = lines[boxStartIndex + 4];

      int topWidth = topBorder.length();
      requireThat(line1.length(), "line1.length").isEqualTo(topWidth);
      requireThat(line2.length(), "line2.length").isEqualTo(topWidth);
      requireThat(line3.length(), "line3.length").isEqualTo(topWidth);
      requireThat(bottomBorder.length(), "bottomBorder.length").isEqualTo(topWidth);

      requireThat(line1, "line1").isEqualTo("│ Short                       │");
      requireThat(line2, "line2").isEqualTo("│ Medium length               │");
      requireThat(line3, "line3").isEqualTo("│ Very long content line here │");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE produces additionalContext with box output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void producesAdditionalContext() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nTest content";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String additionalContext = result.additionalContext();
      requireThat(additionalContext, "additionalContext").isNotNull();
      requireThat(additionalContext, "additionalContext").contains("Test content");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with whitespace-only marker is not recognized.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void whitespaceOnlyMarkerNotRecognized() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "   #BOX_COMPUTE\nContent";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE marker must be at start of first line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void markerMustBeAtStartOfFirstLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "echo 'test'\n#BOX_COMPUTE\nContent";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with single character content produces valid box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleCharacterContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nX";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();

      String[] lines = message.split("\n");
      int boxStartIndex = findBoxStartIndex(lines);
      requireThat(boxStartIndex, "boxStartIndex").isGreaterThanOrEqualTo(0);

      String contentLine = lines[boxStartIndex + 1];
      requireThat(contentLine, "contentLine").contains("X");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with very long content does not truncate.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void veryLongContentNotTruncated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String longContent = "A".repeat(100);
      String command = "#BOX_COMPUTE\n" + longContent;
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains(longContent);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Finds the index where the box starts in the message lines.
   * Looks for the first line starting with top-left corner.
   *
   * @param lines the message split into lines
   * @return the index of the top border line, or -1 if not found
   */
  private int findBoxStartIndex(String[] lines)
  {
    for (int i = 0; i < lines.length; ++i)
    {
      if (lines[i].startsWith("╭"))
        return i;
    }
    return -1;
  }

  /**
   * Verifies that BOX_COMPUTE with box-drawing characters in content produces valid box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void contentWithBoxDrawingCharacters() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\n─── Header\n│ Column │";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains("─── Header").contains("│ Column │");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE handles mixed ASCII and emoji correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void mixedAsciiAndEmojiContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nStatus: ✅ Complete\nTasks: 🏆 10/10";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();
      requireThat(message, "message").contains("Status: ✅ Complete").contains("Tasks: 🏆 10/10");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that BOX_COMPUTE with blank line produces box with empty content line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void blankLineInContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ComputeBoxLines handler = new ComputeBoxLines(scope);
      String command = "#BOX_COMPUTE\nLine 1\n\nLine 3";
      BashHandler.Result result = handler.check(command, "", null, null, "session1");

      requireThat(result.blocked(), "blocked").isTrue();
      String message = result.reason();

      String[] lines = message.split("\n", -1);
      int boxStartIndex = findBoxStartIndex(lines);
      requireThat(boxStartIndex, "boxStartIndex").isGreaterThanOrEqualTo(0);

      requireThat(lines[boxStartIndex + 1], "line1").contains("Line 1");
      requireThat(lines[boxStartIndex + 3], "line3").contains("Line 3");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
