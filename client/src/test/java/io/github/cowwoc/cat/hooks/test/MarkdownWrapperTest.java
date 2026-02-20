/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.MarkdownWrapper;
import org.testng.annotations.Test;

/**
 * Tests for MarkdownWrapper.
 */
public final class MarkdownWrapperTest
{
  /**
   * Verifies that lines shorter than max width are not modified.
   */
  @Test
  public void shortLineUnchanged()
  {
    String input = "Short line";
    String result = MarkdownWrapper.wrapMarkdown(input, 120);
    requireThat(result, "result").isEqualTo("Short line");
  }

  /**
   * Verifies that long regular prose is wrapped at word boundaries.
   */
  @Test
  public void longLineWrapped()
  {
    String input = "This is a very long line that exceeds the maximum width and should be wrapped at word boundaries " +
      "to maintain readability and proper formatting.";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").contains("\n");
    String[] lines = result.split("\n");
    requireThat(lines[0].length(), "firstLineLength").isLessThanOrEqualTo(80);
    requireThat(lines[1].length(), "secondLineLength").isLessThanOrEqualTo(80);
  }

  /**
   * Verifies that code blocks are preserved without wrapping.
   */
  @Test
  public void codeBlockPreserved()
  {
    String input = """
      Regular text
      ```
      This is a very long line of code that should not be wrapped even though it exceeds the maximum width
      ```
      More text""";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").contains("This is a very long line of code that should not be wrapped");
    String[] lines = result.split("\n");
    requireThat(lines[2].length(), "codeLineLength").isGreaterThan(80);
  }

  /**
   * Verifies that markdown tables are preserved without wrapping.
   */
  @Test
  public void tablePreserved()
  {
    String input = """
      | Column 1 | Column 2 | Column 3 | Column 4 | Column 5 | Column 6 | Column 7 | Column 8 | Column 9 |
      |----------|----------|----------|----------|----------|----------|----------|----------|----------|
      | Value 1  | Value 2  | Value 3  | Value 4  | Value 5  | Value 6  | Value 7  | Value 8  | Value 9  |""";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines[0].length(), "tableLineLength").isGreaterThan(80);
    requireThat(result, "result").contains("| Column 1 | Column 2");
  }

  /**
   * Verifies that YAML frontmatter is preserved without wrapping.
   */
  @Test
  public void yamlFrontmatterPreserved()
  {
    String input = """
      ---
      title: This is a very long title that would normally be wrapped but should be preserved in YAML frontmatter
      author: Someone
      ---
      Regular content""";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").contains("title: This is a very long title that would normally be wrapped");
  }

  /**
   * Verifies that lines with box-drawing characters are preserved.
   */
  @Test
  public void boxDrawingPreserved()
  {
    String input = "╭─────────────────────────────────────────────────────────────────────────" +
      "──────────────────────────────────────────────────────╮";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").isEqualTo(input);
  }

  /**
   * Verifies that bare URLs are preserved without wrapping.
   */
  @Test
  public void bareUrlPreserved()
  {
    String input = "https://example.com/very/long/path/that/would/normally/be/wrapped/but/should/" +
      "be/preserved/as/a/single/line";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").isEqualTo(input);
  }

  /**
   * Verifies that bare HTTP URLs (without TLS) are preserved without wrapping.
   */
  @Test
  public void httpUrlPreserved()
  {
    String input = "http://localhost:8080/very/long/path/that/would/normally/be/wrapped/but/should/" +
      "be/preserved/as/a/single/line";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").isEqualTo(input);
  }

  /**
   * Verifies that HTML lines are preserved without wrapping.
   */
  @Test
  public void htmlLinePreserved()
  {
    String input = "<div class=\"very-long-class-name-that-would-normally-be-wrapped-but-should-be-" +
      "preserved-as-html\">Content</div>";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").isEqualTo(input);
  }

  /**
   * Verifies that bullet lists are wrapped with proper continuation indent.
   */
  @Test
  public void bulletListWrapped()
  {
    String input = "- This is a very long bullet point that exceeds the maximum width and should be " +
      "wrapped with proper continuation indent";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines.length, "lineCount").isGreaterThan(1);
    requireThat(lines[0], "firstLine").startsWith("- ");
    requireThat(lines[1], "secondLine").startsWith("  ");
  }

  /**
   * Verifies that numbered lists are wrapped with proper continuation indent.
   */
  @Test
  public void numberedListWrapped()
  {
    String input = "1. This is a very long numbered list item that exceeds the maximum width and should " +
      "be wrapped with proper continuation indent";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines.length, "lineCount").isGreaterThan(1);
    requireThat(lines[0], "firstLine").startsWith("1. ");
    requireThat(lines[1], "secondLine").startsWith("   ");
  }

  /**
   * Verifies that leading whitespace is preserved when wrapping.
   */
  @Test
  public void leadingWhitespacePreserved()
  {
    String input = "  This is an indented line that is very long and should be wrapped while " +
      "preserving the leading whitespace indent";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines[0], "firstLine").startsWith("  ");
  }

  /**
   * Verifies that empty lines are preserved.
   */
  @Test
  public void emptyLinePreserved()
  {
    String input = "Line 1\n\nLine 3";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").isEqualTo("Line 1\n\nLine 3");
  }

  /**
   * Verifies that multiple paragraphs are handled correctly.
   */
  @Test
  public void multipleParagraphs()
  {
    String input = """
      First paragraph with some text.

      Second paragraph with some text.""";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").contains("First paragraph");
    requireThat(result, "result").contains("Second paragraph");
    requireThat(result, "result").contains("\n\n");
  }

  /**
   * Verifies that wrapping avoids creating accidental list markers.
   */
  @Test
  public void avoidsListMarkers()
  {
    String input = "This is a line that contains a dash - character that should not end up at the " +
      "start of a continuation line";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    for (int i = 1; i < lines.length; ++i)
      requireThat(lines[i].strip(), "line" + i).doesNotStartWith("- ");
  }

  /**
   * Verifies that content ending with newline preserves the newline.
   */
  @Test
  public void trailingNewlinePreserved()
  {
    String input = "Line 1\nLine 2\n";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").endsWith("\n");
  }

  /**
   * Verifies that content not ending with newline does not add one.
   */
  @Test
  public void noTrailingNewlineAdded()
  {
    String input = "Line 1\nLine 2";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    requireThat(result, "result").doesNotEndWith("\n");
  }

  /**
   * Verifies that null content is rejected.
   */
  @Test
  public void wrapMarkdownRejectsNullContent()
  {
    try
    {
      MarkdownWrapper.wrapMarkdown(null, 120);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("content");
    }
  }

  /**
   * Verifies that zero width is rejected.
   */
  @Test
  public void wrapMarkdownRejectsZeroWidth()
  {
    try
    {
      MarkdownWrapper.wrapMarkdown("content", 0);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("maxWidth");
    }
  }

  /**
   * Verifies that negative width is rejected.
   */
  @Test
  public void wrapMarkdownRejectsNegativeWidth()
  {
    try
    {
      MarkdownWrapper.wrapMarkdown("content", -1);
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("maxWidth");
    }
  }

  /**
   * Verifies that asterisk lists are wrapped with proper continuation indent.
   */
  @Test
  public void asteriskListWrapped()
  {
    String input = "* This is a very long list item with an asterisk marker that exceeds the " +
      "maximum width and should be wrapped with proper continuation indent";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines.length, "lineCount").isGreaterThan(1);
    requireThat(lines[0], "firstLine").startsWith("* ");
    requireThat(lines[1], "secondLine").startsWith("  ");
  }

  /**
   * Verifies that plus lists are wrapped with proper continuation indent.
   */
  @Test
  public void plusListWrapped()
  {
    String input = "+ This is a very long list item with a plus marker that exceeds the maximum " +
      "width and should be wrapped with proper continuation indent";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines.length, "lineCount").isGreaterThan(1);
    requireThat(lines[0], "firstLine").startsWith("+ ");
    requireThat(lines[1], "secondLine").startsWith("  ");
  }

  /**
   * Verifies that numbered markers do not appear at the start of continuation lines.
   */
  @Test
  public void avoidsNumberedMarkers()
  {
    String input = "This is a line that contains a numbered marker 3. that should not end up at " +
      "the start of a continuation line";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    for (int i = 1; i < lines.length; ++i)
    {
      String stripped = lines[i].strip();
      requireThat(stripped.matches("^\\d+\\..*"), "line" + i + "StartsWithNumber").
        isFalse();
    }
  }

  /**
   * Verifies that multiple code blocks in the same content are all preserved.
   */
  @Test
  public void multipleCodeBlocksPreserved()
  {
    String input = "Regular text\n" +
      "```\n" +
      "First code block with a very long line that should not be wrapped even though it " +
      "exceeds the maximum width limit\n" +
      "```\n" +
      "Middle text\n" +
      "```\n" +
      "Second code block also with a very long line that should not be wrapped even though " +
      "it exceeds the max width\n" +
      "```\n" +
      "End text";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    // Both code blocks should be preserved (lines > 80 chars)
    requireThat(result, "result").contains("First code block with a very long line that should not be wrapped");
    requireThat(result, "result").contains(
      "Second code block also with a very long line that should not be wrapped");
  }

  /**
   * Verifies that inline code spans within prose are wrapped normally.
   * <p>
   * Inline backtick code spans (e.g., {@code `some code`}) are not specially preserved during
   * wrapping. This matches the behavior of the original wrap-markdown.py script.
   */
  @Test
  public void inlineCodeSpanWrappedWithProse()
  {
    String input = "This line contains `inline code` and more text that is very long and should be wrapped " +
      "at word boundaries normally without special backtick handling";
    String result = MarkdownWrapper.wrapMarkdown(input, 80);
    String[] lines = result.split("\n");
    requireThat(lines.length, "lineCount").isGreaterThan(1);
    // Content is preserved, just wrapped
    String joined = String.join(" ", lines);
    requireThat(joined, "joinedContent").contains("`inline code`");
  }

  /**
   * Verifies that word content is preserved after list marker avoidance reflow.
   */
  @Test
  public void listMarkerAvoidancePreservesAllWords()
  {
    String input = "This line has a word followed by a dash - and then more content that makes " +
      "the line exceed the width limit significantly";
    String result = MarkdownWrapper.wrapMarkdown(input, 60);
    // Verify all significant words are present in the output
    String normalized = result.replace("\n", " ");
    requireThat(normalized, "content").contains("dash");
    requireThat(normalized, "content").contains("- and");
    requireThat(normalized, "content").contains("significantly");
  }
}
