package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Token counter utility for markdown files.
 * <p>
 * Counts tokens using the cl100k_base encoding (used by GPT-4 and Claude).
 * Outputs JSON with token counts per file.
 * <p>
 * Usage: java -cp cat-hooks.jar io.github.cowwoc.cat.hooks.TokenCounter file1.md file2.md
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
   * @throws IOException if file reading fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length == 0)
    {
      System.err.println("Usage: TokenCounter file1.md file2.md ...");
      System.exit(1);
      return;
    }

    EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    Encoding encoding = registry.getEncoding("cl100k_base").orElseThrow(
        () -> new IllegalStateException("cl100k_base encoding not found"));

    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode result = mapper.createObjectNode();

    for (String filePath : args)
    {
      requireThat(filePath, "filePath").isNotNull();
      int tokenCount = countTokens(filePath, encoding);
      result.put(filePath, tokenCount);
    }

    System.out.println(mapper.writeValueAsString(result));
  }

  /**
   * Counts tokens in a file using the specified encoding.
   *
   * @param filePath the path to the file
   * @param encoding the encoding to use for tokenization
   * @return the number of tokens
   * @throws IOException if file reading fails
   * @throws IllegalArgumentException if filePath is blank or file does not exist
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
