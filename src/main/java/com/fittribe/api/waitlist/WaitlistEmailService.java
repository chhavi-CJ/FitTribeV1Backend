package com.fittribe.api.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class WaitlistEmailService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Async
    public void sendConfirmation(String toEmail, String referralCode, int position) {
        try {
            String html = buildHtml(referralCode, position);
            String subject = "You're in line at Wynners — #" + position;

            String body = """
                {
                    "from": "Chhavi from Wynners <onboarding@resend.dev>",
                    "to": ["%s"],
                    "subject": "%s",
                    "html": "%s"
                }
                """.formatted(toEmail, subject, html.replace("\"", "\\\"").replace("\n", ""));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_API_URL))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("Sent waitlist confirmation to {}", toEmail);
            } else {
                log.error("Resend API error for {}: {} {}", toEmail, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send waitlist email to " + toEmail, e);
        }
    }

    private String buildHtml(String code, int position) {
        String shareUrl = "https://wynners.in/join?ref=" + code;
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Georgia, serif; max-width:520px; margin:0 auto; padding:2rem; color:#111110;">
              <h1 style="font-size:1.8rem; font-weight:400; line-height:1.2; margin-bottom:1rem;">
                You're <em style="color:#1D9E75;">in line.</em>
              </h1>
              <p style="font-family:Arial,sans-serif; font-size:1rem; line-height:1.6; color:#444441;">
                Your position: <strong>#%d</strong>
              </p>
              <p style="font-family:Arial,sans-serif; font-size:1rem; line-height:1.6; color:#444441;">
                You're on the Wynners founding cohort waitlist. 100 spots — half earned by invites, half earned in the app.
              </p>
              <p style="font-family:Arial,sans-serif; font-size:1rem; line-height:1.6; color:#444441;">
                Want to climb faster? Share your invite link. Every friend who joins bumps you up the queue:
              </p>
              <p style="font-family:Arial,sans-serif; font-size:1rem;">
                <a href="%s" style="color:#1D9E75;">%s</a>
              </p>
              <p style="font-family:Arial,sans-serif; font-size:0.85rem; color:#888780; margin-top:2rem;">
                — Chhavi, founder of Wynners<br>
                <em>We don't count the days you missed. We count the days you came back.</em>
              </p>
            </body>
            </html>
            """.formatted(position, shareUrl, shareUrl);
    }
}