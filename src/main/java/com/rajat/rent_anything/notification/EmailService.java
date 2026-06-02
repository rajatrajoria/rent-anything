package com.rajat.rent_anything.notification;

/**
 * Abstraction for sending application emails.
 *
 * Implementations may use different email providers
 * such as Gmail SMTP, AWS SES, SendGrid, Mailgun, etc.
 *
 * Using an interface keeps the application decoupled
 * from any specific email delivery provider.
 */
public interface EmailService {

    /**
     * Sends an email to the specified recipient.
     *
     * @param to recipient email address
     * @param subject email subject
     * @param body email content
     */
    void sendEmail(String to, String subject, String body);
}