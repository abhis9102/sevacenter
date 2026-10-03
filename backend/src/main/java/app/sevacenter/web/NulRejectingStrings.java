package app.sevacenter.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Every JSON string is checked for NUL ({@code \u0000}): Postgres can't store it in text, so it
 * reached the database and failed as a 500 (found by DAST, M2). Now it's a 400 at parse time,
 * for every request body at once instead of per field.
 */
@Configuration
public class NulRejectingStrings {

    @Bean
    JacksonModule nulRejectingStringsModule() {
        return new SimpleModule("nul-rejecting-strings").addDeserializer(String.class, new Deserializer());
    }

    static final class Deserializer extends StdScalarDeserializer<String> {

        Deserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            String value = StringDeserializer.instance.deserialize(p, ctxt);
            if (value != null && value.indexOf('\0') >= 0) {
                return (String) ctxt.handleWeirdStringValue(String.class, value, "NUL characters are not allowed");
            }
            return value;
        }
    }
}
