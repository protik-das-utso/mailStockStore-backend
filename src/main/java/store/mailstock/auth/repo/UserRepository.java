package store.mailstock.auth.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import store.mailstock.auth.entity.Role;
import store.mailstock.auth.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Admin search: optional role membership + free-text over email/full name, properly paged. */
    @Query("select u from User u where (:role is null or :role member of u.roles) "
            + "and (:q is null or lower(u.email) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))) order by u.id desc")
    Page<User> search(@Param("role") Role role, @Param("q") String q, Pageable pageable);
}
