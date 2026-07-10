package store.mailstock.telegram;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, Long> {
    Optional<TelegramLink> findByChatId(Long chatId);
    Optional<TelegramLink> findByUserId(Long userId);
    void deleteByChatId(Long chatId);
    boolean existsByUserId(Long userId);
}
