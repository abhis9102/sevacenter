package app.sevacenter.donation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import app.sevacenter.web.InvalidFieldException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Donor PAN protection (ADR 0012). The database never sees a PAN in clear:
 * <ul>
 *   <li><b>Encryption:</b> AES-256-GCM, a random 96-bit nonce per value, the tenant id as
 *       associated data. A ciphertext copied into another tenant's row fails to decrypt.</li>
 *   <li><b>Blind index:</b> HMAC-SHA256 under a <i>separate</i> key, so "same donor PAN" can be
 *       matched without decrypting, and the index alone can't be brute-forced without that key.</li>
 * </ul>
 * Keys come from the environment (KMS in M6). The app refuses to start without valid ones and
 * never logs a key or a PAN.
 */
@Component
public class PanProtection {

    /** 4th character: the holder type (P person, C company, H HUF, F firm, A AOP, T trust, ...). */
    private static final Pattern PAN = Pattern.compile("^[A-Z]{3}[ABCFGHJLPT][A-Z][0-9]{4}[A-Z]$");
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec indexKey;

    public PanProtection(@Value("${sevacenter.pan.encryption-key}") String encryptionKey,
                         @Value("${sevacenter.pan.index-key}") String indexKey) {
        byte[] enc = key("sevacenter.pan.encryption-key", encryptionKey);
        byte[] idx = key("sevacenter.pan.index-key", indexKey);
        if (Arrays.equals(enc, idx)) {
            throw new IllegalStateException("PAN encryption and index keys must be different");
        }
        this.encryptionKey = new SecretKeySpec(enc, "AES");
        this.indexKey = new SecretKeySpec(idx, "HmacSHA256");
    }

    /** Upper-cases, strips spaces, validates; the error never echoes the value. */
    public static String normalise(String pan) {
        String p = pan == null ? "" : pan.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
        if (!PAN.matcher(p).matches()) {
            throw new InvalidFieldException("donorPan", "donorPan must be a valid PAN (e.g. ABCPE1234F)");
        }
        return p;
    }

    public String encrypt(long tenantId, String pan) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId));
            byte[] sealed = cipher.doFinal(pan.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + sealed.length)
                    .put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PAN encryption failed", e);
        }
    }

    public String decrypt(long tenantId, String stored) {
        if (stored == null || !stored.startsWith("v1:")) {
            throw new IllegalStateException("unknown PAN ciphertext format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(3));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            cipher.updateAAD(aad(tenantId));
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong tenant, tampered, or wrong key: never return anything partial.
            throw new IllegalStateException("PAN ciphertext could not be authenticated");
        }
    }

    public String index(long tenantId, String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            return HexFormat.of().formatHex(mac.doFinal((tenantId + ":" + pan).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PAN index failed", e);
        }
    }

    /** What may be shown or stored in clear: the last 4 characters. */
    public static String last4(String pan) {
        return pan.substring(pan.length() - 4);
    }

    public static String masked(String last4) {
        return "XXXXXX" + last4;
    }

    private static byte[] aad(long tenantId) {
        return ("sevacenter:donor_pan:tenant:" + tenantId).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] key(String name, String base64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64 == null ? "" : base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be base64 (generate: openssl rand -base64 32)");
        }
        if (key.length != 32) {
            throw new IllegalStateException(name + " must be 32 bytes (generate: openssl rand -base64 32)");
        }
        boolean constant = true;
        for (byte b : key) {
            constant &= b == key[0];
        }
        if (constant) {
            throw new IllegalStateException(name + " is a placeholder, not a key (generate: openssl rand -base64 32)");
        }
        return key;
    }
}
