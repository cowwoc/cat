/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for DisplayUtils functionality.
 * <p>
 * Tests are designed to verify requirements rather than implementation details.
 * For example, tests verify that borders align correctly rather than asserting
 * specific emoji width values (which are terminal-dependent configuration).
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class DisplayUtilsTest
{
  /**
   * Verifies that empty string has width 0.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyStringHasWidthZero() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      int width = display.displayWidth("");
      requireThat(width, "width").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that ASCII text width equals string length.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void asciiTextWidthEqualsLength() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      int width1 = display.displayWidth("hello");
      requireThat(width1, "width1").isEqualTo(5);

      int width2 = display.displayWidth("test string");
      requireThat(width2, "width2").isEqualTo(11);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildLine pads content shorter than minWidth.
   * <p>
   * Tests structural property: content shorter than minWidth is padded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildLinePadsShortContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String line = display.buildLine("hi", 20);

      requireThat(line, "line").startsWith("│").endsWith("│").contains("hi");

      int lineWidth = display.displayWidth(line);
      requireThat(lineWidth, "lineWidth").isGreaterThan(display.displayWidth("│ hi │"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildLine produces consistent visual width for ASCII and emoji content.
   * <p>
   * The real requirement: borders must align. Two lines with different content
   * at the same minWidth should produce the same visual width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildLineConsistentWidthAcrossContentTypes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);

      String asciiLine = display.buildLine("hello", 20);
      String emojiLine = display.buildLine("🐱 cat", 20);

      int asciiWidth = display.displayWidth(asciiLine);
      int emojiWidth = display.displayWidth(emojiLine);

      requireThat(asciiWidth, "asciiWidth").isEqualTo(emojiWidth);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildLine does not truncate content exceeding minWidth.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildLineDoesNotTruncateLongContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);

      String longContent = "a".repeat(25);
      String longLine = display.buildLine(longContent, 20);
      String shortLine = display.buildLine("short", 20);

      int longWidth = display.displayWidth(longLine);
      int shortWidth = display.displayWidth(shortLine);

      requireThat(longWidth, "longWidth").isGreaterThan(shortWidth);
      requireThat(longLine, "longLine").contains(longContent);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildTopBorder produces correct border characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildTopBorderProducesTopBorder() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String border = display.buildTopBorder(10);

      requireThat(border, "border").startsWith("╭").endsWith("╮").contains("─");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildBottomBorder produces correct border characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildBottomBorderProducesBottomBorder() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String border = display.buildBottomBorder(10);

      requireThat(border, "border").startsWith("╰").endsWith("╯").contains("─");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that border visual width matches buildLine visual width at same minWidth.
   * <p>
   * This is the requirement: borders must align with content lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void bordersAlignWithBuildLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);

      String line = display.buildLine("content", 20);
      String topBorder = display.buildTopBorder(20);
      String bottomBorder = display.buildBottomBorder(20);

      int lineWidth = display.displayWidth(line);
      int topWidth = display.displayWidth(topBorder);
      int bottomWidth = display.displayWidth(bottomBorder);

      requireThat(topWidth, "topWidth").isEqualTo(lineWidth);
      requireThat(bottomWidth, "bottomWidth").isEqualTo(lineWidth);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderBox produces multi-line output with header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderBoxProducesMultiLineOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderBox("Test Header", List.of("Line 1", "Line 2"));
      String[] lines = result.split("\n");

      requireThat(lines.length, "lineCount").isGreaterThanOrEqualTo(4);
      requireThat(lines[0], "firstLine").startsWith("╭");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
      requireThat(result, "result").contains("Line 1").contains("Line 2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar produces string of correct length.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildProgressBarProducesCorrectLength() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);

      String bar1 = display.buildProgressBar(50, 10);
      requireThat(bar1, "bar1").length().isEqualTo(10);

      String bar2 = display.buildProgressBar(75, 20);
      requireThat(bar2, "bar2").length().isEqualTo(20);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar at 0 percent is all empty characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildProgressBarZeroPercentAllEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String bar = display.buildProgressBar(0, 10);

      requireThat(bar, "bar").isEqualTo("░".repeat(10)).doesNotContain("█");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar at maximum percent produces mostly filled bar.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildProgressBarMaxPercentMostlyFilled() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String bar = display.buildProgressBar(99, 10);

      requireThat(bar, "bar").length().isEqualTo(10);

      int emptyCount = 0;
      for (int i = 0; i < bar.length(); ++i)
      {
        if (bar.charAt(i) == '░')
          ++emptyCount;
      }
      requireThat(emptyCount, "emptyCount").isLessThanOrEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar with fill characters and empty characters for partial progress.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildProgressBarPartialProgressHasBothCharacters() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String bar = display.buildProgressBar(50, 10);

      requireThat(bar, "bar").contains("█").contains("░");
      requireThat(bar, "bar").length().isEqualTo(10);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- Error path tests ---

  /**
   * Verifies that buildLine with null content throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void buildLineWithNullContentThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildLine(null, 20);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderBox with null header throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void buildHeaderBoxWithNullHeaderThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildHeaderBox(null, List.of("content"));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderBox with null contentLines throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void buildHeaderBoxWithNullContentLinesThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildHeaderBox("Header", null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar with percent below 0 throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void buildProgressBarNegativePercentThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildProgressBar(-1, 10);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar with percent above 100 throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void buildProgressBarOverHundredPercentThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildProgressBar(101, 10);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar with zero width throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void buildProgressBarZeroWidthThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildProgressBar(50, 0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildProgressBar with negative width throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void buildProgressBarNegativeWidthThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildProgressBar(50, -5);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that displayWidth with null text throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void displayWidthWithNullTextThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.displayWidth(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- Boundary condition tests ---

  /**
   * Verifies that buildProgressBar at 100 percent throws IllegalArgumentException.
   * <p>
   * The isBetween(0, 100) validation uses exclusive upper bound [0, 100),
   * so 100 is not a valid value.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void buildProgressBarHundredPercentThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      display.buildProgressBar(100, 10);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that ratingToCircles clamps values below 1 to produce one filled circle.
   */
  @Test
  public void ratingToCirclesClampsNegativeToOne()
  {
    String result = DisplayUtils.ratingToCircles(-5);
    requireThat(result, "result").isEqualTo("●○○○○");
  }

  /**
   * Verifies that ratingToCircles clamps values above 5 to produce five filled circles.
   */
  @Test
  public void ratingToCirclesClampsAboveFiveToMax()
  {
    String result = DisplayUtils.ratingToCircles(10);
    requireThat(result, "result").isEqualTo("●●●●●");
  }

  /**
   * Verifies that ratingToCircles clamps zero to produce one filled circle.
   */
  @Test
  public void ratingToCirclesClampsZeroToOne()
  {
    String result = DisplayUtils.ratingToCircles(0);
    requireThat(result, "result").isEqualTo("●○○○○");
  }

  /**
   * Verifies that ratingToCircles at boundary value 1 produces one filled circle.
   */
  @Test
  public void ratingToCirclesMinBoundary()
  {
    String result = DisplayUtils.ratingToCircles(1);
    requireThat(result, "result").isEqualTo("●○○○○");
  }

  /**
   * Verifies that ratingToCircles at boundary value 5 produces five filled circles.
   */
  @Test
  public void ratingToCirclesMaxBoundary()
  {
    String result = DisplayUtils.ratingToCircles(5);
    requireThat(result, "result").isEqualTo("●●●●●");
  }

  /**
   * Verifies that ratingToCircles at middle value 3 produces three filled circles.
   */
  @Test
  public void ratingToCirclesMiddleValue()
  {
    String result = DisplayUtils.ratingToCircles(3);
    requireThat(result, "result").isEqualTo("●●●○○");
  }

  /**
   * Verifies that buildHeaderBox output starts with top-left corner and ends with bottom-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderBoxHasBoxStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderBox("Title", List.of("content line"));
      String[] lines = result.split("\n");

      requireThat(lines[0], "firstLine").startsWith("╭");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderBox with empty content list produces valid box.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderBoxWithEmptyContentProducesValidBox() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderBox("Title", List.of());
      String[] lines = result.split("\n");

      requireThat(lines[0], "firstLine").startsWith("╭");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildLine with zero minWidth produces valid line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildLineWithZeroMinWidth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String line = display.buildLine("content", 0);

      requireThat(line, "line").startsWith("│").endsWith("│").contains("content");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that variation selectors do not add extra width.
   * <p>
   * The character sequence for a checkbox emoji with variation selector
   * should not count the zero-width variation selector as an additional column.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void variationSelectorDoesNotAddWidth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      // ☑️ is ☑ (U+2611) + variation selector (U+FE0F)
      int width = display.displayWidth("☑️");
      // Should be 1 or 2 (emoji width) but not 3 (which would mean the variation selector
      // was counted as a separate character)
      requireThat(width, "width").isLessThanOrEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that box-drawing characters each have width 1.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void boxDrawingCharactersHaveWidthOne() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);

      requireThat(display.displayWidth("─"), "horizontalWidth").isEqualTo(1);
      requireThat(display.displayWidth("│"), "verticalWidth").isEqualTo(1);
      requireThat(display.displayWidth("╭╮"), "topCornersWidth").isEqualTo(2);
      requireThat(display.displayWidth("╰╯"), "bottomCornersWidth").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- buildSeparator tests (from Python TestBuildSeparator) ---

  /**
   * Verifies that buildSeparator returns a non-empty string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSeparatorReturnsString() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSeparator(10);
      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSeparator starts with left T-connector.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSeparatorStartsWithLeftConnector() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSeparator(10);
      requireThat(result, "result").startsWith("├");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSeparator ends with right T-connector.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSeparatorEndsWithRightConnector() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSeparator(10);
      requireThat(result, "result").endsWith("┤");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSeparator contains horizontal dashes.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSeparatorContainsDashes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSeparator(10);
      requireThat(result, "result").contains("─");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSeparator dash count equals minWidth plus 2.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSeparatorDashCountIsWidthPlusTwo() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSeparator(10);
      long dashCount = result.chars().filter(c -> c == '─').count();
      requireThat(dashCount, "dashCount").isEqualTo(12L);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSeparator works correctly with various widths.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSeparatorVariousWidths() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      for (int width : new int[]{5, 20, 50})
      {
        String result = display.buildSeparator(width);
        requireThat(result, "result").startsWith("├").endsWith("┤");
        long dashCount = result.chars().filter(c -> c == '─').count();
        requireThat(dashCount, "dashCount").isEqualTo((long) (width + 2));
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- buildHeaderTop tests (from Python TestBuildHeaderTop) ---

  /**
   * Verifies that buildHeaderTop returns a non-empty string.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderTopReturnsString() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderTop("Header", 20);
      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderTop starts with top-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderTopStartsWithCorner() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderTop("Test", 20);
      requireThat(result, "result").startsWith("╭");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderTop ends with top-right corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderTopEndsWithCorner() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderTop("Test", 20);
      requireThat(result, "result").endsWith("╮");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderTop contains the header text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderTopContainsHeaderText() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderTop("My Header", 30);
      requireThat(result, "result").contains("My Header");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderTop has prefix dashes before header text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderTopHasPrefixDashes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderTop("Test", 20);
      requireThat(result, "result").contains("─── ");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildHeaderTop has suffix dashes after header text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildHeaderTopHasSuffixDashes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildHeaderTop("Test", 20);
      String[] parts = result.split("Test ");
      requireThat(parts.length, "parts.length").isEqualTo(2);
      requireThat(parts[1], "suffixPart").endsWith("╮").contains("─");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // --- buildSimpleBox tests (from Python TestBuildSimpleBox) ---

  /**
   * Verifies that buildSimpleBox produces a box with top-left corner containing
   * icon and title, and bottom-left corner footer.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxBasicStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("📊", "Test Title", List.of("Content line"));
      String[] lines = result.split("\n");
      requireThat(lines[0], "firstLine").startsWith("╭").endsWith("╮");
      requireThat(result, "result").contains("📊 Test Title");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰").endsWith("╯");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox includes all content lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxWithMultipleContentLines() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("✅", "Header",
        List.of("Line 1", "Line 2", "Line 3"));
      requireThat(result, "result").contains("Line 1").contains("Line 2").contains("Line 3");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox content lines have consistent visual width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxContentLinesHaveConsistentWidth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("📊", "TEST",
        List.of("Short", "Medium length content", "Longer content line here"));
      String[] lines = result.split("\n");
      List<String> contentLines = new ArrayList<>();
      for (String line : lines)
      {
        if (line.startsWith("│"))
          contentLines.add(line);
      }
      if (!contentLines.isEmpty())
      {
        int firstWidth = display.displayWidth(contentLines.get(0));
        for (String contentLine : contentLines)
        {
          int width = display.displayWidth(contentLine);
          requireThat(width, "contentLineWidth").isEqualTo(firstWidth);
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox header and footer have the same visual width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxHeaderAndFooterSameWidth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("✅", "TITLE", List.of("Content"));
      String[] lines = result.split("\n");
      int headerWidth = display.displayWidth(lines[0]);
      int footerWidth = display.displayWidth(lines[lines.length - 1]);
      requireThat(headerWidth, "headerWidth").isEqualTo(footerWidth);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox produces lines with consistent visual width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxAllLinesSameWidth() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("🔧", "Settings",
        List.of("Option 1: value", "Option 2: longer value here", "Option 3: x"));
      String[] lines = result.split("\n");
      int firstWidth = display.displayWidth(lines[0]);
      for (String line : lines)
      {
        int width = display.displayWidth(line);
        requireThat(width, "lineWidth").isEqualTo(firstWidth);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox handles empty content list.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxEmptyContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("ℹ️", "INFO", List.of());
      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isGreaterThanOrEqualTo(2);
      requireThat(lines[0], "firstLine").startsWith("╭");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox handles emoji icons correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxEmojiIcon() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String result = display.buildSimpleBox("🐱", "CAT", List.of("Content"));
      requireThat(result, "result").contains("🐱 CAT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that buildSimpleBox handles long content lines and maintains
   * consistent visual width across all lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildSimpleBoxLongContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DisplayUtils display = new DisplayUtils(scope);
      String longLine = "A".repeat(100);
      String result = display.buildSimpleBox("📊", "Test", List.of(longLine));
      requireThat(result, "result").contains(longLine);
      String[] lines = result.split("\n");
      int firstWidth = display.displayWidth(lines[0]);
      for (String line : lines)
      {
        int width = display.displayWidth(line);
        requireThat(width, "lineWidth").isEqualTo(firstWidth);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
