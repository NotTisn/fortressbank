package com.uit.notificationservice.service;

import com.uit.notificationservice.dto.EmailNotificationRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {


    private final JavaMailSender mailSender;

    @Value("${app.email.from-email:noreply@fortressbank.com}")
    private String fromEmail;

    @Value("${app.email.from-name:FortressBank}")
    private String fromName;
    
    @Value("${app.email.reply-to:}")
    private String replyTo;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    /**
     * Send email notification with HTML template
     *
     * @param request Email notification request with title, content, and optional elements
     */
    public void sendEmailNotification(EmailNotificationRequest request) {
        try {
            log.info("Preparing to send email via SendGrid");
            log.info("From: {} <{}>", fromName, fromEmail);
            log.info("To: {}", request.getRecipientEmail());

            // Load HTML template
            String htmlTemplate = loadEmailTemplate();

            // Build HTML content with template variables
            String htmlContent = buildHtmlContent(htmlTemplate, request);

            // Create and send email via SendGrid
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            // Set sender (must be verified in SendGrid)
            helper.setFrom(fromEmail, fromName);
            helper.setTo(request.getRecipientEmail());
            helper.setSubject(request.getTitle());
            helper.setText(htmlContent, true); // true = HTML content
            
            // Set reply-to if configured
            if (replyTo != null && !replyTo.isEmpty()) {
                helper.setReplyTo(replyTo);
            }

            // Send via SendGrid
            mailSender.send(message);

            log.info("Email sent successfully via SendGrid to: {}", request.getRecipientEmail());

        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", request.getRecipientEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to send email notification", e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", request.getRecipientEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to send email notification", e);
        }
    }

    /**
     * Send simple email notification (title + content only)
     */
    public void sendSimpleEmail(String recipientEmail, String title, String content) {
        EmailNotificationRequest request = EmailNotificationRequest.builder()
                .recipientEmail(recipientEmail)
                .title(title)
                .content(content)
                .build();

        sendEmailNotification(request);
    }

    /**
     * Load HTML email template from resources
     * Uses InputStream to work with both file system and JAR files
     */
    private String loadEmailTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email-notification.html");
            
            // Use InputStream instead of getFile() to work with JAR files
            try (var inputStream = resource.getInputStream()) {
                byte[] bytes = inputStream.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to load email template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }

    /**
     * Build HTML content by replacing template variables
     * Simple string replacement - can be replaced with Thymeleaf/Freemarker for complex scenarios
     */
    private String buildHtmlContent(String template, EmailNotificationRequest request) {
        String html = template;

        // Replace basic fields
        html = html.replace("{{title}}", escapeHtml(request.getTitle()));
        html = html.replace("{{content}}", escapeHtml(request.getContent()).replace("\n", "<br>"));
        html = html.replace("{{timestamp}}", LocalDateTime.now().format(TIMESTAMP_FORMATTER));

        // Handle badge
        if (request.getBadge() != null && !request.getBadge().isEmpty()) {
            String badgeClass = getBadgeClass(request.getBadge());
            html = html.replace("{{#if badge}}", "");
            html = html.replace("{{/if}}", "");
            html = html.replace("{{badge}}", escapeHtml(request.getBadge()));
            html = html.replace("{{badgeClass}}", badgeClass);
        } else {
            // Remove badge section if not present
            html = removeConditionalBlock(html, "{{#if badge}}", "{{/if}}");
        }

        // Handle additional info
        if (request.getAdditionalInfo() != null && !request.getAdditionalInfo().isEmpty()) {
            String infoRows = buildInfoRows(request.getAdditionalInfo());
            html = html.replace("{{#if additionalInfo}}", "");
            html = html.replace("{{/if}}", "");
            html = html.replace("{{#each additionalInfo}}", infoRows);
            html = html.replace("{{/each}}", "");
        } else {
            html = removeConditionalBlock(html, "{{#if additionalInfo}}", "{{/if}}");
        }

        // Handle CTA button
        if (request.getCtaUrl() != null && !request.getCtaUrl().isEmpty()) {
            html = html.replace("{{#if ctaUrl}}", "");
            html = html.replace("{{/if}}", "");
            html = html.replace("{{ctaUrl}}", escapeHtml(request.getCtaUrl()));
            html = html.replace("{{ctaText}}", escapeHtml(request.getCtaText() != null ? request.getCtaText() : "View Details"));
        } else {
            html = removeConditionalBlock(html, "{{#if ctaUrl}}", "{{/if}}");
        }

        return html;
    }

    /**
     * Build info rows HTML
     */
    private String buildInfoRows(List<EmailNotificationRequest.InfoRow> infoRows) {
        StringBuilder sb = new StringBuilder();

        for (EmailNotificationRequest.InfoRow row : infoRows) {
            sb.append("<div class=\"info-row\">\n");
            sb.append("    <span class=\"info-label\">").append(escapeHtml(row.getLabel())).append("</span>\n");
            sb.append("    <span class=\"info-value\">").append(escapeHtml(row.getValue())).append("</span>\n");
            sb.append("</div>\n");
        }

        return sb.toString();
    }

    /**
     * Get CSS class for badge based on type
     */
    private String getBadgeClass(String badgeType) {
        return switch (badgeType.toUpperCase()) {
            case "SUCCESS", "COMPLETED" -> "success-badge";
            case "FAILED", "ERROR" -> "failed-badge";
            default -> "info-badge";
        };
    }

    /**
     * Remove conditional blocks from template
     */
    private String removeConditionalBlock(String html, String startMarker, String endMarker) {
        int startIndex = html.indexOf(startMarker);
        if (startIndex != -1) {
            int endIndex = html.indexOf(endMarker, startIndex);
            if (endIndex != -1) {
                return html.substring(0, startIndex) + html.substring(endIndex + endMarker.length());
            }
        }
        return html;
    }

    /**
     * Escape HTML special characters to prevent XSS
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
