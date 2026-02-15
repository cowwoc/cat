package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.util.List;

/**
 * Result of processing hook input, containing the JSON output and any warnings.
 *
 * @param output the JSON output string to write to stdout
 * @param warnings warnings to write to stderr
 */
public record HookResult(String output, List<String> warnings)
{
  /**
   * Creates a new hook result.
   *
   * @throws NullPointerException if {@code output} or {@code warnings} are null
   */
  public HookResult
  {
    requireThat(output, "output").isNotNull();
    requireThat(warnings, "warnings").isNotNull();
  }

  /**
   * Creates a result with no warnings.
   *
   * @param output the JSON output string
   * @return a HookResult with no warnings
   * @throws NullPointerException if {@code output} is null
   */
  public static HookResult withoutWarnings(String output)
  {
    requireThat(output, "output").isNotNull();
    return new HookResult(output, List.of());
  }
}
