package store.mailstock.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import store.mailstock.auth.entity.User;
import store.mailstock.common.exception.ApiException;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static User currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof User u))
            throw ApiException.unauthorized("Not authenticated");
        return u;
    }

    public static Long currentUserId() { return currentUser().getId(); }
}
