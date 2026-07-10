package store.mailstock.notification.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable p);
    long countByUserIdAndReadAtIsNull(Long userId);
}
