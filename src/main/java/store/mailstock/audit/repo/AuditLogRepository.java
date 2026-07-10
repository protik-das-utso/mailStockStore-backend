package store.mailstock.audit.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByActorIdOrderByIdDesc(Long actorId, Pageable p);
    Page<AuditLog> findByActionOrderByIdDesc(String action, Pageable p);
}
