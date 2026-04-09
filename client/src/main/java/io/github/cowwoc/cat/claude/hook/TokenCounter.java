/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.cat.claude.hook.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.tool.MainClaudeTool;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Token counter utility for markdown files.
 * <p>
 * Counts tokens using the cl100k_base encoding (used by GPT-4 and Claude).
 * Outputs JSON with token counts per file.
 * <p>
 * Usage: java -cp cat-client-2.1.jar io.github.cowwoc.cat.claude.hook.TokenCounter file1.md file2.md
 * <p>
 * Output format:
 * <pre>
 * {
 *   "file1.md": 1234,
 *   "file2.md": 5678
 * }
 * </pre>
 */
public final class TokenCounter
{
  /**
   * Prevents instantiation.
   */
  private TokenCounter()
  {
  }

  /**
   * Entry point for token counting.
   *
   * @param args file paths to count tokens for
   * @throws NullPointerException if {@code args} contains a null element
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (IllegalArgumentException | IOException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(TokenCounter.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Counts tokens in the specified files and writes a JSON result to the output stream.
   *
   * @param scope the JVM scope
   * @param args  file paths to count tokens for
   * @param out   the output stream to write to
   * @throws NullPointerException     if any of {@code scope}, {@code args}, or {@code out} are null, or if
   *                                  {@code args} contains a null element
   * @throws IllegalArgumentException if no file paths are provided
   * @throws IOException              if file reading fails
   */
  public static void run(JvmScope scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();

    if (args.length == 0)
      throw new IllegalArgumentException("Usage: TokenCounter file1.md file2.md ...");

    EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    Encoding encoding = registry.getEncoding("cl100k_base").orElseThrow(
      () -> new IllegalStateException("cl100k_base encoding not found"));

    JsonMapper mapper = scope.getJsonMapper();
    ObjectNode result = mapper.createObjectNode();

    for (String filePath : args)
    {
      requireThat(filePath, "filePath").isNotNull();
      int tokenCount = countTokens(filePath, encoding);
      result.put(filePath, tokenCount);
    }

    out.println(mapper.writeValueAsString(result));
  }

  /**
   * Counts tokens in a file using the specified encoding.
   *
   * @param filePath the path to the file
   * @param encoding the encoding to use for tokenization
   * @return the number of tokens
   * @throws IOException if file reading fails
   * @throws NullPointerException if {@code filePath} or {@code encoding} are null
   * @throws IllegalArgumentException if {@code filePath} is blank or file does not exist
   */
  private static int countTokens(String filePath, Encoding encoding) throws IOException
  {
    requireThat(filePath, "filePath").isNotBlank();
    requireThat(encoding, "encoding").isNotNull();

    Path path = Paths.get(filePath);
    if (!Files.exists(path))
      throw new IllegalArgumentException("File does not exist: " + filePath);

    String content = Files.readString(path);
    return encoding.countTokens(content);
  }
}
