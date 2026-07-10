package store.mailstock.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, String> {
    @Modifying
    @Query("delete from TelegramLinkCode c where c.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
