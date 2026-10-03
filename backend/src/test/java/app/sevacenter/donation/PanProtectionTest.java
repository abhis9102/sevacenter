package app.sevacenter.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import app.sevacenter.web.InvalidFieldException;
import org.junit.jupiter.api.Test;

/** Donor PAN encryption and blind index (ADR 0012). */
class PanProtectionTest {

    private static final String KEY = key(0);
    private static final String INDEX_KEY = key(32);
    private final PanProtection pans = new PanProtection(KEY, INDEX_KEY);

    @Test
    void roundTripsAndNeverRepeatsACiphertext() {
        String a = pans.encrypt(1, "ABCPE1234F");
        String b = pans.encrypt(1, "ABCPE1234F");
        assertThat(a).isNotEqualTo(b).doesNotContain("ABCPE1234F", "1234");
        assertThat(pans.decrypt(1, a)).isEqualTo("ABCPE1234F");
    }

    /** The tenant id is bound in as associated data: a ciphertext moved to another tenant is useless. */
    @Test
    void aCiphertextMovedToAnotherTenantFailsToDecrypt() {
        String sealed = pans.encrypt(1, "ABCPE1234F");
        assertThatThrownBy(() -> pans.decrypt(2, sealed)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("ABCPE");
    }

    @Test
    void tamperedCiphertextAndOtherKeysFail() {
        String sealed = pans.encrypt(1, "ABCPE1234F");
        byte[] raw = Base64.getDecoder().decode(sealed.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> pans.decrypt(1, tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PanProtection(key(64), INDEX_KEY).decrypt(1, sealed))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theBlindIndexMatchesWithinATenantOnly() {
        assertThat(pans.index(1, "ABCPE1234F")).isEqualTo(pans.index(1, "ABCPE1234F"))
                .isNotEqualTo(pans.index(2, "ABCPE1234F"))
                .isNotEqualTo(pans.index(1, "ABCPE1234G"))
                .doesNotContain("1234");
    }

    @Test
    void panFormatIsValidatedWithoutEchoingTheValue() {
        assertThat(PanProtection.normalise(" abcpe 1234f ")).isEqualTo("ABCPE1234F");
        for (String bad : new String[] {"ABCDE1234F", "ABCP1234F", "ABCPE12345", "1BCPE1234F", "", null}) {
            assertThatThrownBy(() -> PanProtection.normalise(bad)).isInstanceOf(InvalidFieldException.class)
                    .hasMessageNotContaining(bad == null || bad.isEmpty() ? "§" : bad);
        }
    }

    @Test
    void theAppRefusesToStartWithoutRealKeys() {
        assertThatThrownBy(() -> new PanProtection("", INDEX_KEY)).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new PanProtection("not base64!", INDEX_KEY)).hasMessageContaining("base64");
        assertThatThrownBy(() -> new PanProtection(Base64.getEncoder().encodeToString(new byte[16]), INDEX_KEY))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new PanProtection(Base64.getEncoder().encodeToString(new byte[32]), INDEX_KEY))
                .hasMessageContaining("placeholder");
        assertThatThrownBy(() -> new PanProtection(KEY, KEY)).hasMessageContaining("different");
    }

    private static String key(int start) {
        byte[] k = new byte[32];
        for (int i = 0; i < 32; i++) {
            k[i] = (byte) (start + i);
        }
        return Base64.getEncoder().encodeToString(k);
    }
}
