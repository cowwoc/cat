package io.github.cowwoc.cat.hooks.licensing;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates Ed25519-signed JWT license tokens.
 * <p>
 * Implements JWT signature verification, expiration checking, and grace period handling.
 */
public final class LicenseValidator
{
  private static final int DEFAULT_GRACE_DAYS = 7;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
  {
  };

  private final Path pluginRoot;
  private final JsonMapper mapper;

  /**
   * Creates a new license validator.
   *
   * @param pluginRoot the plugin root directory containing config/cat-public-key.pem
   * @param mapper the JSON mapper
   * @throws NullPointerException if {@code pluginRoot} or {@code mapper} are null
   */
  public LicenseValidator(Path pluginRoot, JsonMapper mapper)
  {
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(mapper, "mapper").isNotNull();
    this.pluginRoot = pluginRoot;
    this.mapper = mapper;
  }

  /**
   * Validates the license token from the project configuration.
   *
   * @param projectDir the project root directory containing .claude/cat/
   * @return the validation result
   * @throws NullPointerException if projectDir is null
   */
  public LicenseResult validate(Path projectDir)
  {
    requireThat(projectDir, "projectDir").isNotNull();

    // Load config
    Config config;
    try
    {
      config = Config.load(mapper, projectDir);
    }
    catch (Exception _)
    {
      return LicenseResult.indie();
    }

    // Get license token
    String token = config.getString("license");
    if (token.isEmpty())
      return LicenseResult.indie();

    // Find public key
    Path keyPath = pluginRoot.resolve("config").resolve("cat-public-key.pem");
    if (!Files.exists(keyPath))
      return LicenseResult.error("Public key not found");

    // Validate token
    try
    {
      PublicKey publicKey = loadPublicKey(keyPath);
      return validateToken(token, publicKey);
    }
    catch (Exception e)
    {
      return LicenseResult.error("Validation failed: " + e.getMessage());
    }
  }

  /**
   * Loads an Ed25519 public key from a PEM file.
   *
   * @param keyPath the path to the PEM file
   * @return the public key
   * @throws IOException if the file cannot be read
   */
  private PublicKey loadPublicKey(Path keyPath) throws IOException
  {
    try
    {
      String pem = Files.readString(keyPath);
      String base64 = pem.
        replace("-----BEGIN PUBLIC KEY-----", "").
        replace("-----END PUBLIC KEY-----", "").
        replaceAll("\\s", "");

      byte[] decoded = Base64.getDecoder().decode(base64);
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
      KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
      return keyFactory.generatePublic(keySpec);
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Validates a JWT token and extracts tier information.
   *
   * @param token the JWT token
   * @param publicKey the Ed25519 public key
   * @return the validation result
   */
  private LicenseResult validateToken(String token, PublicKey publicKey)
  {
    try
    {
      // Split JWT
      String[] parts = token.split("\\.");
      if (parts.length != 3)
        return LicenseResult.error("Invalid token format");

      String headerB64 = parts[0];
      String payloadB64 = parts[1];
      String signatureB64 = parts[2];

      // Verify signature
      byte[] message = (headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8);
      byte[] signature = base64UrlDecode(signatureB64);

      Signature sig = Signature.getInstance("Ed25519");
      sig.initVerify(publicKey);
      sig.update(message);
      boolean signatureValid = sig.verify(signature);

      if (!signatureValid)
        return LicenseResult.error("Invalid signature");

      // Decode payload
      byte[] payloadBytes = base64UrlDecode(payloadB64);
      String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
      Map<String, Object> payload = mapper.readValue(payloadJson, MAP_TYPE);

      // Extract fields
      String tierString = (String) payload.getOrDefault("tier", "indie");
      Tier tier = Tier.fromString(tierString);
      Object expObj = payload.get("exp");
      Object graceDaysObj = payload.get("grace_days");

      int graceDays = DEFAULT_GRACE_DAYS;
      if (graceDaysObj instanceof Number n)
        graceDays = n.intValue();

      // Check expiration
      if (expObj instanceof Number n)
      {
        long exp = n.longValue();
        Instant expTime = Instant.ofEpochSecond(exp);
        Instant now = Instant.now();

        boolean expired = now.isAfter(expTime);
        boolean inGrace = false;
        String warning = "";
        int daysRemaining;

        if (expired)
        {
          Duration pastExpiry = Duration.between(expTime, now);
          int daysPastExpiry = (int) pastExpiry.toDays();
          Duration remaining = Duration.between(now, expTime);
          daysRemaining = (int) remaining.toDays();

          if (daysPastExpiry <= graceDays)
          {
            inGrace = true;
            warning = "License expired " + daysPastExpiry + " days ago. Renew soon.";
          }
          else
          {
            // Past grace period - fall back to indie
            warning = "License expired. Run /cat:login to renew.";
            tier = Tier.INDIE;
          }
        }
        else
        {
          Duration remaining = Duration.between(now, expTime);
          daysRemaining = (int) remaining.toDays();
        }

        return new LicenseResult(true, tier, expired, inGrace, daysRemaining, "", warning);
      }

      // No expiration - valid indefinitely
      return new LicenseResult(true, tier, false, false, 0, "", "");
    }
    catch (Exception e)
    {
      return LicenseResult.error(e.getMessage());
    }
  }

  /**
   * Decodes base64url without padding.
   *
   * @param data the base64url string
   * @return the decoded bytes
   */
  private byte[] base64UrlDecode(String data)
  {
    int padding = 4 - data.length() % 4;
    if (padding != 4)
      data += "=".repeat(padding);
    return Base64.getUrlDecoder().decode(data);
  }
}
