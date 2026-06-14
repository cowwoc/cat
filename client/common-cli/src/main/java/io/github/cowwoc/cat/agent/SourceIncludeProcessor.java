/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.agent;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Expands source-only include directives in agent-facing Markdown and TOML files.
 */
public final class SourceIncludeProcessor
{
  private static final Pattern INCLUDE_DIRECTIVE =
    Pattern.compile("^\\s*<!--\\s*cat:include\\s+([^>]+?)\\s*-->\\s*$");
  private static final int MAX_INCLUDE_DEPTH = 8;

  private SourceIncludeProcessor()
  {
  }

  /**
   * Expands include directives in a source file.
   *
   * @param sourceFile the file that contains {@code content}
   * @param content the file content
   * @param allowedTarget returns true if a resolved include target is allowed
   * @return content with include directives expanded
   * @throws IOException if an included file cannot be read
   * @throws IllegalArgumentException if any parameter is null
   * @throws IllegalStateException if an include directive is invalid
   */
  public static String expand(Path sourceFile, String content, Predicate<Path> allowedTarget) throws IOException
  {
    return expand(sourceFile, content, allowedTarget, UnaryOperator.identity());
  }

  /**
   * Expands include directives in a source file.
   *
   * @param sourceFile the file that contains {@code content}
   * @param content the file content
   * @param allowedTarget returns true if a resolved include target is allowed
   * @param contentFilter transforms each included file before recursively processing it
   * @return content with include directives expanded
   * @throws IOException if an included file cannot be read
   * @throws IllegalArgumentException if any parameter is null
   * @throws IllegalStateException if an include directive is invalid
   */
  public static String expand(Path sourceFile, String content, Predicate<Path> allowedTarget,
    UnaryOperator<String> contentFilter) throws IOException
  {
    requireThat(sourceFile, "sourceFile").isNotNull();
    requireThat(content, "content").isNotNull();
    requireThat(allowedTarget, "allowedTarget").isNotNull();
    requireThat(contentFilter, "contentFilter").isNotNull();

    Path normalizedSource = sourceFile.toAbsolutePath().normalize();
    return expand(normalizedSource, content, allowedTarget, contentFilter, List.of());
  }

  /**
   * Recursively expands include directives while tracking visited files.
   *
   * @param sourceFile the current source file
   * @param content the file content
   * @param allowedTarget returns true if a resolved include target is allowed
   * @param contentFilter transforms included file content before recursive expansion
   * @param stack include chain used for recursion detection
   * @return the expanded content
   * @throws IOException if reading an included file fails
   * @throws IllegalStateException if recursion, excessive depth, or invalid targets are detected
   */
  private static String expand(Path sourceFile, String content, Predicate<Path> allowedTarget,
    UnaryOperator<String> contentFilter, List<Path> stack) throws IOException
  {
    if (stack.contains(sourceFile))
      throw new IllegalStateException("Recursive cat:include detected: " + stack + " -> " + sourceFile);
    if (stack.size() >= MAX_INCLUDE_DEPTH)
      throw new IllegalStateException("cat:include nesting too deep in " + sourceFile);

    List<Path> nextStack = Stream.concat(stack.stream(), Stream.of(sourceFile)).toList();
    StringBuilder output = new StringBuilder(content.length());
    for (String line : content.split("\\R", -1))
    {
      Matcher matcher = INCLUDE_DIRECTIVE.matcher(line);
      if (matcher.matches())
      {
        Path target = resolveTarget(sourceFile, matcher.group(1));
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
          throw new IllegalStateException("cat:include target does not exist: " + target);
        if (!allowedTarget.test(target))
          throw new IllegalStateException("cat:include target is not allowed: " + target);
        String targetContent = FileContentCache.readString(target);
        if (FrontmatterUtils.extractFrontmatter(targetContent) != null)
          throw new IllegalStateException("cat:include target must not contain YAML frontmatter: " + target);
        targetContent = contentFilter.apply(targetContent);
        output.append(stripTrailingLineSeparator(expand(target, targetContent, allowedTarget,
          contentFilter, nextStack))).append('\n');
      }
      else
        output.append(line).append('\n');
    }
    return output.toString();
  }

  /**
   * Resolves raw include target text against source file location.
   *
   * @param sourceFile file containing include directive
   * @param rawTarget raw target captured from directive
   * @return normalized absolute target path
   * @throws IllegalStateException if target is blank or absolute
   */
  private static Path resolveTarget(Path sourceFile, String rawTarget)
  {
    String target = rawTarget.strip();
    if (target.isEmpty())
      throw new IllegalStateException("cat:include target must not be blank in " + sourceFile);
    Path targetPath = Path.of(target);
    if (targetPath.isAbsolute())
      throw new IllegalStateException("cat:include target must be relative to the source file: " + target);
    return sourceFile.getParent().resolve(targetPath).toAbsolutePath().normalize();
  }

  /**
   * Removes one trailing line separator from expanded include content.
   *
   * @param text text to trim
   * @return text without final line separator
   */
  private static String stripTrailingLineSeparator(String text)
  {
    return text.replaceFirst("\\R\\z", "");
  }
}
