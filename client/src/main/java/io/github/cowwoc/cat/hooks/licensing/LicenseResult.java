/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.licensing;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Result of license validation.
 * <p>
 * Contains validation status, tier information, expiration details, and any warnings or errors.
 *
 * @param valid whether the license signature is valid
 * @param tier the license tier
 * @param expired whether the license has passed its expiration date
 * @param inGrace whether the license is expired but within the grace period
 * @param daysRemaining days until expiration (negative if expired)
 * @param error error message if validation failed, empty string if no error
 * @param warning warning message (e.g., grace period expiring), empty string if no warning
 */
public record LicenseResult(
  boolean valid,
  Tier tier,
  boolean expired,
  boolean inGrace,
  int daysRemaining,
  String error,
  String warning)
{
  /**
   * Creates a new license result with validation.
   *
   * @throws NullPointerException if tier, error, or warning is null
   */
  public LicenseResult
  {
    requireThat(tier, "tier").isNotNull();
    requireThat(error, "error").isNotNull();
    requireThat(warning, "warning").isNotNull();
  }

  /**
   * Creates a default result for core tier (no license).
   *
   * @return a license result indicating core tier
   */
  public static LicenseResult core()
  {
    return new LicenseResult(false, Tier.CORE, false, false, 0, "", "");
  }

  /**
   * Creates an error result for core tier.
   *
   * @param errorMessage the error message
   * @return a license result with error
   * @throws NullPointerException if errorMessage is null
   */
  public static LicenseResult error(String errorMessage)
  {
    requireThat(errorMessage, "errorMessage").isNotNull();
    return new LicenseResult(false, Tier.CORE, false, false, 0, errorMessage, "");
  }
}
