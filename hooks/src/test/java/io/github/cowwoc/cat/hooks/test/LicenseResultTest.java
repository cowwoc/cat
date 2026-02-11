package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.licensing.LicenseResult;
import io.github.cowwoc.cat.hooks.licensing.Tier;

import org.testng.annotations.Test;

/**
 * Tests for {@link LicenseResult}.
 */
public final class LicenseResultTest
{
  /**
   * Verifies that indie factory returns correct defaults.
   */
  @Test
  public void indieFactoryReturnsCorrectDefaults()
  {
    LicenseResult result = LicenseResult.indie();

    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
    requireThat(result.expired(), "expired").isFalse();
    requireThat(result.inGrace(), "inGrace").isFalse();
    requireThat(result.daysRemaining(), "daysRemaining").isEqualTo(0);
    requireThat(result.error(), "error").isEqualTo("");
    requireThat(result.warning(), "warning").isEqualTo("");
  }

  /**
   * Verifies that error factory returns correct state.
   */
  @Test
  public void errorFactoryReturnsCorrectState()
  {
    LicenseResult result = LicenseResult.error("Invalid token");

    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
    requireThat(result.expired(), "expired").isFalse();
    requireThat(result.inGrace(), "inGrace").isFalse();
    requireThat(result.daysRemaining(), "daysRemaining").isEqualTo(0);
    requireThat(result.error(), "error").isEqualTo("Invalid token");
    requireThat(result.warning(), "warning").isEqualTo("");
  }

  /**
   * Verifies that constructor rejects null tier.
   */
  @Test
  public void constructorRejectsNullTier()
  {
    try
    {
      new LicenseResult(true, null, false, false, 0, "", "");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("tier");
    }
  }

  /**
   * Verifies that constructor rejects null error.
   */
  @Test
  public void constructorRejectsNullError()
  {
    try
    {
      new LicenseResult(true, Tier.TEAM, false, false, 0, null, "");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("error");
    }
  }

  /**
   * Verifies that constructor rejects null warning.
   */
  @Test
  public void constructorRejectsNullWarning()
  {
    try
    {
      new LicenseResult(true, Tier.TEAM, false, false, 0, "", null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("warning");
    }
  }
}
