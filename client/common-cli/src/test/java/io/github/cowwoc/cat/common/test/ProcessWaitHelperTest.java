/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.common.test;

import io.github.cowwoc.cat.tool.util.ProcessWaitHelper;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link ProcessWaitHelper}.
 */
public final class ProcessWaitHelperTest
{
  /**
   * Verifies that the helper returns true when the process exits promptly.
   *
   * @throws IOException if process launch fails
   * @throws InterruptedException if interrupted while waiting
   */
  @Test
  public void returnsTrueWhenProcessExitsPromptly() throws IOException, InterruptedException
  {
    try (Process process = new ProcessBuilder("bash", "-c", "exit 0").start())
    {
      boolean completed = ProcessWaitHelper.waitForProcessOrFailure(process, () -> false,
        System.nanoTime() + Duration.ofSeconds(1).toNanos(), Duration.ofMillis(50));
      requireThat(completed, "completed").isTrue();
    }
  }

  /**
   * Verifies that the helper returns false once the deadline expires.
   *
   * @throws IOException if process launch fails
   * @throws InterruptedException if interrupted while waiting
   */
  @Test
  public void returnsFalseWhenDeadlineExpires() throws IOException, InterruptedException
  {
    try (Process process = new ProcessBuilder("bash", "-c", "sleep 5").start())
    {
      boolean completed = ProcessWaitHelper.waitForProcessOrFailure(process, () -> false,
        System.nanoTime() + Duration.ofMillis(100).toNanos(), Duration.ofMillis(50));
      requireThat(completed, "completed").isFalse();
    }
  }

  /**
   * Verifies that a failure signal returns promptly without waiting for the full timeout.
   * The helper reports whether the process had already exited at the moment the failure was
   * observed, so either boolean result is acceptable here.
   *
   * @throws IOException if process launch fails
   * @throws InterruptedException if interrupted while waiting
   */
  @Test
  public void returnsPromptlyWhenFailureSignalArrives() throws IOException, InterruptedException
  {
    try (Process process = new ProcessBuilder("bash", "-c", "sleep 5").start())
    {
      AtomicBoolean failure = new AtomicBoolean(false);
      Thread signaler = new Thread(() ->
      {
        try
        {
          Thread.sleep(50);
        }
        catch (InterruptedException _)
        {
          Thread.currentThread().interrupt();
        }
        failure.set(true);
        process.destroyForcibly();
      });
      signaler.start();
      long start = System.nanoTime();
      try
      {
        ProcessWaitHelper.waitForProcessOrFailure(process, failure::get,
          System.nanoTime() + Duration.ofSeconds(5).toNanos(), Duration.ofMillis(50));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        requireThat(elapsed.compareTo(Duration.ofMillis(300)), "elapsed").isLessThan(0);
      }
      finally
      {
        signaler.join();
        process.destroyForcibly();
      }
    }
  }
}
