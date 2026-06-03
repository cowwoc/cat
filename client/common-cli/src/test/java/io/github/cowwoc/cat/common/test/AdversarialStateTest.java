/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.common.test;

import io.github.cowwoc.cat.tool.skills.AdversarialState;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests scalar extraction from adversarial hardening artifacts.
 */
public final class AdversarialStateTest
{
  /**
   * Verifies finding severity predicates.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void severityPredicates() throws IOException
  {
    String json = """
      {
        "loopholes": [
          {"name": "a", "severity": "LOW"},
          {"name": "b", "severity": "HIGH"}
        ],
        "disputed": []
      }""";

    requireThat(AdversarialState.run("has-critical-high", json), "hasCriticalHigh").
      isEqualTo("true");
    requireThat(AdversarialState.run("has-medium-low", json), "hasMediumLow").
      isEqualTo("true");
  }

  /**
   * Verifies unresolved dispute detection ignores arbitration-upheld disputes.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void detectsNewDisputes() throws IOException
  {
    String json = """
      {
        "loopholes": [],
        "disputed": [
          {"name": "upheld", "arbitration_verdict": "upheld"},
          {"name": "new"}
        ]
      }""";

    requireThat(AdversarialState.run("has-new-disputes", json), "hasNewDisputes").
      isEqualTo("true");
  }

  /**
   * Verifies rejected-count supports the arbitration report schema.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test
  public void rejectedCountReadsReportScalar() throws IOException
  {
    String json = """
      {
        "round": 2,
        "rejected_count": 3,
        "rejected": [{"name": "a"}]
      }""";

    requireThat(AdversarialState.run("rejected-count", json), "rejectedCount").
      isEqualTo("3");
  }
}
