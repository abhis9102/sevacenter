package app.sevacenter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Exceptions DAST turned into 500s (M2 slice 2) map to client errors. Unit-level: a malformed
 * multipart part and a concurrent delete can't be produced reliably through MockMvc.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void malformedUploadsAreABadRequest() {
        assertThat(handler.onBadUpload().getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void oversizedUploadsAreContentTooLarge() {
        assertThat(handler.onUploadTooLarge().getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void aRowChangedByAConcurrentRequestIsAConflict() {
        assertThat(handler.onConcurrentChange().getStatusCode().value()).isEqualTo(409);
    }
}
