package com.rajat.rent_anything.notification;

public interface EmailService {
    void sendEmail(String to, String subject, String body);
}
