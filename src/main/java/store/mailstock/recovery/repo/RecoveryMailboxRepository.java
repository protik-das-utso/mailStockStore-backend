package store.mailstock.recovery.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import store.mailstock.recovery.entity.RecoveryMailbox;

public interface RecoveryMailboxRepository extends JpaRepository<RecoveryMailbox, Long> {
    List<RecoveryMailbox> findAllByOrderByCreatedAtDesc();
    Optional<RecoveryMailbox> findFirstByEmailIgnoreCaseAndActiveTrue(String email);
    Optional<RecoveryMailbox> findFirstByActiveTrueOrderByCreatedAtAsc();
}
