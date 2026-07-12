package store.mailstock.recovery.service;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import store.mailstock.recovery.entity.RecoveryMailbox;

/**
 * Read-only access to a platform-owned mailbox over POP3 or IMAP. Pulls recent messages and hands back a
 * lightweight view (sender, subject, body, date) — it never deletes, moves, or marks anything. Parsing of
 * Google recovery codes lives in {@link RecoveryCodeService}.
 *
 * <p>Protocol is inferred: IMAP when the host looks like IMAP or the port is 993/143, else POP3. IMAP is
 * strongly preferred for webmail/temp-mail hosts (e.g. kuku.lu) whose POP3 only exposes new/undownloaded
 * mail and often shows an empty inbox.
 */
@Service
@Slf4j
public class PopMailReader {

    private static boolean isImap(RecoveryMailbox mb) {
        String host = mb.getHost() == null ? "" : mb.getHost().toLowerCase();
        Integer port = mb.getPort();
        if (host.contains("imap")) return true;
        if (host.contains("pop")) return false;
        return port != null && (port == 993 || port == 143);
    }

    /** "IMAP" or "POP3" — which protocol this mailbox will be read with (for diagnostics). */
    public static String protocolName(RecoveryMailbox mb) {
        return isImap(mb) ? "IMAP" : "POP3";
    }

    /** Newest-first snapshot of the most recent messages, capped so a busy mailbox stays cheap to read. */
    public List<Msg> fetchRecent(RecoveryMailbox mb, String plainPassword, int max) {
        boolean ssl = Boolean.TRUE.equals(mb.getSsl());
        String base = isImap(mb) ? "imap" : "pop3";
        String protocol = ssl ? base + "s" : base;

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", mb.getHost());
        props.put("mail." + protocol + ".port", String.valueOf(mb.getPort()));
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "15000");
        if (ssl) props.put("mail." + protocol + ".ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;
        try {
            store = session.getStore(protocol);
            store.connect(mb.getHost(), mb.getPort(), mb.getUsername(), plainPassword);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] messages = pickMessages(inbox, max);

            List<Msg> out = new ArrayList<>(messages.length);
            for (Message m : messages) {
                try {
                    Instant received = m.getReceivedDate() != null ? m.getReceivedDate().toInstant()
                            : (m.getSentDate() != null ? m.getSentDate().toInstant() : Instant.now());
                    out.add(new Msg(senderOf(m), m.getSubject() == null ? "" : m.getSubject(),
                            bodyText(m), received));
                } catch (Exception perMsg) {
                    log.warn("[RECOVERY] skipping unreadable message: {}", perMsg.toString());
                }
            }
            out.sort(Comparator.comparing(Msg::receivedAt).reversed());
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(base.toUpperCase() + " read failed: " + e.getMessage(), e);
        } finally {
            try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) {}
            try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Grab the most recent {@code max} messages. Some IMAP servers (e.g. kuku.lu) don't report a
     * message count on folder-open — {@code getMessageCount()} returns -1 — which would make the normal
     * {@code getMessages(from, count)} build a negative-size range. When the count is unusable we fall
     * back to an IMAP SEARCH (all messages since epoch), which enumerates without relying on the count.
     */
    private static Message[] pickMessages(Folder inbox, int max) throws Exception {
        int count = inbox.getMessageCount();
        if (count > 0) {
            int from = Math.max(1, count - max + 1);
            return inbox.getMessages(from, count);
        }
        if (count == 0) return new Message[0];
        // count < 0 (unknown): SEARCH "SINCE 1-Jan-1970" returns everything without needing EXISTS.
        Message[] all = inbox.search(new ReceivedDateTerm(ComparisonTerm.GE, new Date(0)));
        if (all.length <= max) return all;
        return java.util.Arrays.copyOfRange(all, all.length - max, all.length);
    }

    private static String senderOf(Message m) throws Exception {
        if (m.getFrom() == null || m.getFrom().length == 0) return "";
        if (m.getFrom()[0] instanceof InternetAddress ia && ia.getAddress() != null) return ia.getAddress();
        return m.getFrom()[0].toString();
    }

    /** Flattens a message to text (prefers text/plain, falls back to de-tagged HTML). */
    private static String bodyText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            return isHtml(part) ? stripHtml(s) : s;
        }
        if (content instanceof Multipart mp) {
            StringBuilder plain = new StringBuilder();
            StringBuilder html = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                Object c = bp.getContent();
                if (c instanceof Multipart) {
                    plain.append('\n').append(bodyText(bp));
                } else if (c instanceof String s) {
                    if (isHtml(bp)) html.append('\n').append(s); else plain.append('\n').append(s);
                }
            }
            return !plain.toString().isBlank() ? plain.toString() : stripHtml(html.toString());
        }
        return "";
    }

    private static boolean isHtml(Part p) throws Exception {
        return p.isMimeType("text/html");
    }

    private static String stripHtml(String html) {
        return html.replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Minimal read-only view of one message. */
    public record Msg(String from, String subject, String body, Instant receivedAt) {}
}
