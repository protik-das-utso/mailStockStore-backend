package store.mailstock.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import store.mailstock.audit.entity.AuditLog;
import store.mailstock.audit.repo.AuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;

    @Transactional
    public void log(Long actorId, String action, String entity, String entityId, String metadata, String ip) {
        repo.save(AuditLog.builder()
                .actorId(actorId).action(action).entity(entity)
                .entityId(entityId).metadata(metadata).ip(ip).build());
    }
}
