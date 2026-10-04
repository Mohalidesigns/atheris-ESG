package com.esg.notifications;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email channel via SMTP (TRD §6.6.3; Mailpit locally, SES/SendGrid SMTP in production). Spring only creates a
 * JavaMailSender when {@code spring.mail.host} is set; without it, deliveries are recorded as skipped, not sent.
 */
@Component
public class EmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    /** Outside production every message can be redirected to one inbox (MAIL_ALWAYS_TO), so tests never reach real people. */
    private final String alwaysTo;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${esg.notifications.email.from}") String from,
                       @Value("${esg.notifications.email.always-to:}") String alwaysTo) {
        this.mailSender = mailSender;
        this.from = from;
        this.alwaysTo = alwaysTo == null ? "" : alwaysTo.trim();
    }

    public boolean configured() {
        return mailSender.getIfAvailable() != null;
    }

    public SendResult send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return SendResult.notConfigured("Email is not configured (spring.mail.host unset)");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(alwaysTo.isEmpty() ? to : alwaysTo);
            message.setSubject(alwaysTo.isEmpty() ? subject : "[for " + to + "] " + subject);
            message.setText(body);
            sender.send(message);
            return SendResult.sent();
        } catch (RuntimeException e) {
            return SendResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
