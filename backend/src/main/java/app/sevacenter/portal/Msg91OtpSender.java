package app.sevacenter.portal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Login codes by SMS through MSG91's Flow API (ADR 0018). Indian regulation (TRAI DLT) requires a
 * registered sender id and a pre-approved template, configured at MSG91; we send only the template
 * id and the code. Available only when the auth key and template id are set (environment only).
 */
@Component
class Msg91OtpSender implements OtpSender {

    private static final String FLOW_URL = "https://control.msg91.com/api/v5/flow";

    private final String authKey;
    private final String templateId;
    private final RestClient http;

    Msg91OtpSender(@Value("${sevacenter.sms.msg91.auth-key:}") String authKey,
                   @Value("${sevacenter.sms.msg91.template-id:}") String templateId) {
        this.authKey = authKey.strip();
        this.templateId = templateId.strip();
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(timeouts).build();
    }

    @Override
    public OtpChannel channel() {
        return OtpChannel.SMS;
    }

    @Override
    public boolean available() {
        return !authKey.isEmpty() && !templateId.isEmpty();
    }

    @Override
    public void send(String to, String code, String trustName) {
        // MSG91 takes the number without '+'; DLT templates carry the trust-neutral wording.
        http.post().uri(FLOW_URL)
                .header("authkey", authKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("template_id", templateId, "short_url", "0",
                        "recipients", List.of(Map.of("mobiles", to.substring(1), "otp", code))))
                .retrieve()
                .toBodilessEntity();
    }
}
