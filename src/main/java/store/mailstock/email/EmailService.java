package store.mailstock.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ResendClient resendClient;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}") private String from;
    @Value("${app.mail.from-name}") private String fromName;
    @Value("${app.frontend-url}") private String frontendUrl;

    @Async
    public void sendVerification(String to, String name, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(to, "Verify your Mail Stock Store email",
                "verify", Map.of("name", name, "link", link));
    }

    @Async
    public void sendPasswordReset(String to, String name, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(to, "Reset your Mail Stock Store password",
                "reset", Map.of("name", name, "link", link));
    }

    @Async
    public void sendGeneric(String to, String subject, String template, Map<String, Object> model) {
        send(to, subject, template, model);
    }

    private void send(String to, String subject, String template, Map<String, Object> model) {
        try {
            Context ctx = new Context();
            model.forEach(ctx::setVariable);
            String html = templateEngine.process("email/" + template, ctx);
            String fromHeader = fromName + " <" + from + ">";
            if (resendClient.send(fromHeader, to, subject, html)) {
                log.info("Sent '{}' email to {}", template, to);
            }
        } catch (Exception e) {
            log.error("Failed sending email to {}: {}", to, e.getMessage(), e);
        }
    }
}
