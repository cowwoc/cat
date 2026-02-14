package io.github.cowwoc.cat.hooks;

/**
 * A handler that processes hook input and produces hook output.
 */
@FunctionalInterface
public interface HookHandler
{
  /**
   * Processes hook input and returns the result with any warnings.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  HookResult run(HookInput input, HookOutput output);
}
