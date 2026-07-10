package store.mailstock.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.auth.entity.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUserId(Long userId);
}
