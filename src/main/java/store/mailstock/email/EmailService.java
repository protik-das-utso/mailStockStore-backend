package store.mailstock.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}") private String from;
    @Value("${app.mail.from-name}") private String fromName;
    @Value("${app.frontend-url}") private String frontendUrl;

    @Async
    public void sendVerification(String to, String name, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(to, "Verify your MailStock.store email",
                "verify", Map.of("name", name, "link", link));
    }

    @Async
    public void sendPasswordReset(String to, String name, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(to, "Reset your MailStock.store password",
                "reset", Map.of("name", name, "link", link));
    }

    @Async
    public void sendGeneric(String to, String subject, String template, Map<String, Object> model) {
        send(to, subject, template, model);
    }

    private void send(String to, String subject, String template, Map<String, Object> model) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
            Context ctx = new Context();
            model.forEach(ctx::setVariable);
            String html = templateEngine.process("email/" + template, ctx);
            h.setFrom(from, fromName);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
            log.info("Sent '{}' email to {}", template, to);
        } catch (Exception e) {
            log.error("Failed sending email to {}: {}", to, e.getMessage(), e);
        }
    }
}
