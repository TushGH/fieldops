package com.fieldops.notification;

import java.net.URI;
import java.util.UUID;
import com.fieldops.audit.SecurityAudit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountMail {
    private final JavaMailSender sender;
    private final SecurityAudit audit;
    private final String origin;
    private final String from;

    public AccountMail(JavaMailSender sender, SecurityAudit audit,
                       @Value("${fieldops.web-origin}") String origin, @Value("${fieldops.mail-from}") String from) {
        var uri = URI.create(origin);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("Invalid web origin");
        this.sender = sender;
        this.audit = audit;
        this.origin = origin.replaceAll("/$", "");
        this.from = from;
    }

    public void afterCommit(String email, String path, String token, UUID target) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Mail requires a transaction");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Continue with FieldOps");
        message.setText("Open this link to continue. You must confirm the action on the page.\n\n"
                + origin + path + "#token=" + token + "\n\nIf you did not request this, you can ignore this email.");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    sender.send(message);
                    audit.independent(null, null, "EMAIL_DELIVERY", "SUCCESS", target);
                } catch (RuntimeException exception) {
                    // The committed challenge/invitation remains recoverable by a rate-limited resend.
                    // Never log the exception: SMTP diagnostics may include the message/token.
                    audit.independent(null, null, "EMAIL_DELIVERY", "FAILURE", target);
                }
            }
        });
    }
}
