package store.mailstock.recovery.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import store.mailstock.recovery.entity.RecoveryLink;

public interface RecoveryLinkRepository extends JpaRepository<RecoveryLink, Long> {
    Optional<RecoveryLink> findByToken(String token);
    List<RecoveryLink> findAllByOrderByCreatedAtDesc();
    List<RecoveryLink> findByMailboxId(Long mailboxId);
    Optional<RecoveryLink> findFirstByAccountEmailIgnoreCaseAndMailboxIdOrderByCreatedAtDesc(String accountEmail, Long mailboxId);
}
