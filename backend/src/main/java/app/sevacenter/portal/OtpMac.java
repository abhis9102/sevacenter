package app.sevacenter.portal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 under SEVACENTER_OTP_KEY (ADR 0018). A 6-digit code has only a million values, so a
 * plain hash in the database would fall to a loop in a second; with a server-held key, a stolen
 * table is useless without the key too. Its own key: one key per kind of secret.
 */
@Component
class OtpMac {

    private final SecretKeySpec key;

    OtpMac(@Value("${sevacenter.otp.key}") String base64Key) {
        byte[] k;
        try {
            k = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("sevacenter.otp.key must be base64 (generate: openssl rand -base64 32)");
        }
        if (k.length != 32) {
            throw new IllegalStateException("sevacenter.otp.key must be 32 bytes (generate: openssl rand -base64 32)");
        }
        boolean constant = true;
        for (byte b : k) {
            constant &= b == k[0];
        }
        if (constant) {
            throw new IllegalStateException("sevacenter.otp.key is a placeholder, not a key");
        }
        this.key = new SecretKeySpec(k, "HmacSHA256");
    }

    /** Identifies a contact within one trust without storing it. */
    String contact(long tenantId, OtpChannel channel, String contact) {
        return mac("contact|" + tenantId + "|" + channel + "|" + contact);
    }

    /** Bound to the trust and the contact: a code row copied to another contact never matches. */
    String code(long tenantId, String contactMac, String code) {
        return mac("code|" + tenantId + "|" + contactMac + "|" + code);
    }

    private String mac(String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(key);
            return Base64.getEncoder().encodeToString(m.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
