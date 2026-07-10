package store.mailstock.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.auth.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUserId(Long userId);
}
