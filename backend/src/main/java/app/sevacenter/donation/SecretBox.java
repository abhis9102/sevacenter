package app.sevacenter.donation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts third-party credentials a trust gives us (its Razorpay key secret, ADR 0013):
 * AES-256-GCM, random nonce, tenant id + purpose as associated data, so a ciphertext copied to
 * another tenant's row (or used for another purpose) fails to decrypt. Its own key
 * (SEVACENTER_SECRETS_KEY), separate from the PAN key: one key per kind of secret.
 */
@Component
public class SecretBox {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretBox(@Value("${sevacenter.secrets.key}") String base64Key) {
        byte[] k;
        try {
            k = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("sevacenter.secrets.key must be base64 (generate: openssl rand -base64 32)");
        }
        if (k.length != 32) {
            throw new IllegalStateException("sevacenter.secrets.key must be 32 bytes (generate: openssl rand -base64 32)");
        }
        boolean constant = true;
        for (byte b : k) {
            constant &= b == k[0];
        }
        if (constant) {
            throw new IllegalStateException("sevacenter.secrets.key is a placeholder, not a key");
        }
        this.key = new SecretKeySpec(k, "AES");
    }

    public String seal(long tenantId, String purpose, String plaintext) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(tenantId, purpose));
            byte[] sealed = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public String open(long tenantId, String purpose, String stored) {
        if (stored == null || !stored.startsWith("v1:")) {
            throw new IllegalStateException("unknown ciphertext format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(3));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            c.updateAAD(aad(tenantId, purpose));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("ciphertext could not be authenticated");
        }
    }

    private static byte[] aad(long tenantId, String purpose) {
        return ("sevacenter:" + purpose + ":tenant:" + tenantId).getBytes(StandardCharsets.UTF_8);
    }
}
