/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Shared process-wait helper for nested engine runners.
 * <p>
 * The caller-selected 50 ms cadence is intentionally small enough to notice reader failures and
 * finished child processes promptly, but large enough to avoid busy-spin wakeups. This is
 * internal process supervision rather than user-facing polling.
 */
public final class ProcessWaitHelper
{
  private ProcessWaitHelper()
  {
  }

  /**
   * Waits until the process exits, a caller-defined failure signal is raised, or the deadline
   * expires.
   *
   * @param process the child process
   * @param failureDetected returns {@code true} once a reader or callback failure occurred
   * @param deadlineNanos absolute deadline from {@link System#nanoTime()}
   * @param pollInterval maximum duration to block in one wait iteration
   * @return {@code true} if the process exited before the deadline; otherwise {@code false}
   * @throws InterruptedException if interrupted while waiting
   */
  public static boolean waitForProcessOrFailure(Process process, BooleanSupplier failureDetected,
    long deadlineNanos, Duration pollInterval) throws InterruptedException
  {
    requireThat(process, "process").isNotNull();
    requireThat(failureDetected, "failureDetected").isNotNull();
    requireThat(pollInterval, "pollInterval").isNotNull();

    while (true)
    {
      if (failureDetected.getAsBoolean())
        return process.waitFor(0, TimeUnit.MILLISECONDS);
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0)
        return false;
      long waitMillis = Math.min(Duration.ofNanos(remainingNanos).toMillis(),
        pollInterval.toMillis());
      if (waitMillis <= 0)
        waitMillis = 1;
      if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS))
        return true;
    }
  }
}
