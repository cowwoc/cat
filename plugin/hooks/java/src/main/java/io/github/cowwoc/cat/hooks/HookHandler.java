package io.github.cowwoc.cat.hooks;

/**
 * A handler that processes hook input and produces hook output.
 */
@FunctionalInterface
public interface HookHandler
{
  /**
   * Processes hook input and writes the result.
   *
   * @param input the hook input to process
   * @param output the hook output writer
   * @throws NullPointerException if input or output is null
   */
  void run(HookInput input, HookOutput output);
}
