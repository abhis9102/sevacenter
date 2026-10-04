package app.sevacenter.portal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Local development only (ADR 0025): with no SMS provider configured, an "SMS" code is delivered to
 * the local Mailpit inbox (http://localhost:8025) addressed to sms-91XXXXXXXXXX@sms.local, so phone
 * login can be tried end to end. It exists only under the {@code local} profile and steps aside the
 * moment a real SMS provider is configured.
 */
@Component
@Profile("local")
class LocalSmsSink implements OtpSender {

    private final ObjectProvider<JavaMailSender> mail;
    private final ObjectProvider<Msg91OtpSender> realSms;
    private final String from;

    LocalSmsSink(ObjectProvider<JavaMailSender> mail, ObjectProvider<Msg91OtpSender> realSms,
                 @Value("${sevacenter.mail.from:}") String from) {
        this.mail = mail;
        this.realSms = realSms;
        this.from = from.strip();
    }

    @Override
    public OtpChannel channel() {
        return OtpChannel.SMS;
    }

    @Override
    public boolean available() {
        Msg91OtpSender real = realSms.getIfAvailable();
        return (real == null || !real.available()) && !from.isEmpty() && mail.getIfAvailable() != null;
    }

    @Override
    public void send(String to, String code, String trustName) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo("sms-" + to.substring(1) + "@sms.local");
        m.setSubject("[local SMS to " + to + "] " + code + " is your " + trustName + " login code");
        m.setText("Local development stand-in for an SMS to " + to + ".\n\n"
                + "Your login code for " + trustName + " is " + code + ". It expires in 10 minutes.\n");
        mail.getObject().send(m);
    }
}
