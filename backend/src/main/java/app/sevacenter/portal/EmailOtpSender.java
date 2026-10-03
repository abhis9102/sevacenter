package app.sevacenter.portal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Login codes by email over SMTP (ADR 0018): Mailpit locally, Amazon SES in production. Available
 * only when spring.mail.host and a sender address are configured. Plain text, no links: a code is
 * typed, never clicked, so a forwarded or phished email has nothing to follow.
 */
@Component
class EmailOtpSender implements OtpSender {

    private final ObjectProvider<JavaMailSender> mail;
    private final String from;

    EmailOtpSender(ObjectProvider<JavaMailSender> mail, @Value("${sevacenter.mail.from:}") String from) {
        this.mail = mail;
        this.from = from.strip();
    }

    @Override
    public OtpChannel channel() {
        return OtpChannel.EMAIL;
    }

    @Override
    public boolean available() {
        return !from.isEmpty() && mail.getIfAvailable() != null;
    }

    @Override
    public void send(String to, String code, String trustName) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject(code + " is your " + trustName + " login code");
        m.setText("Your login code for " + trustName + " is " + code + ".\n\n"
                + "It expires in 10 minutes. Nobody from the temple will ever ask you for it.\n"
                + "If you didn't ask for this code, you can ignore this email.\n");
        mail.getObject().send(m);
    }
}
