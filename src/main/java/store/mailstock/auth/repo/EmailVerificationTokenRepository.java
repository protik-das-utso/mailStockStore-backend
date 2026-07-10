package store.mailstock.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.auth.entity.EmailVerificationToken;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    void deleteByUserId(Long userId);
}
