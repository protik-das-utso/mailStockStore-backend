package store.mailstock.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import store.mailstock.auth.entity.User;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {
    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || !(a.getPrincipal() instanceof User u)) return Optional.empty();
        return Optional.ofNullable(u.getId());
    }
}
