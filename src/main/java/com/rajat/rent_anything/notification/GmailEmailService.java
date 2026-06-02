package com.rajat.rent_anything.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "gmail")
public class GmailEmailService implements EmailService {

    private final JavaMailSender mailSender;

    public GmailEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an email using Gmail SMTP.
     *
     * Current Implementation:
     * - Uses Spring's JavaMailSender.
     * - Executes asynchronously to avoid blocking API request threads.
     * - Suitable for low to moderate email volumes.
     *
     * Future Improvements:
     * - Queue-based email delivery (Kafka/RabbitMQ).
     * - AWS SES / SendGrid integration.
     * - Retry and dead-letter handling.
     *
     * @param to recipient email address
     * @param subject email subject
     * @param body email body
     */
    @Async("emailExecutor")
    @Override
    public void sendEmail(String to, String subject, String body) {

        try {

            SimpleMailMessage message = new SimpleMailMessage();

            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info(
                    "Email sent successfully to recipient: {}, subject: {}",
                    to,
                    subject
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to send email to recipient: {}, subject: {}",
                    to,
                    subject,
                    ex
            );

            throw ex;
        }
    }
}