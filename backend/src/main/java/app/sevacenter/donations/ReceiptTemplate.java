package app.sevacenter.donations;

import java.util.Map;

import org.apache.commons.text.StringSubstitutor;

/** Fills a donation receipt template, e.g. "Received ${amount} from ${donor}". */
public final class ReceiptTemplate {

    private ReceiptTemplate() {
    }

    public static String render(String template, Map<String, String> values) {
        return StringSubstitutor.replace(template, values);
    }
}
