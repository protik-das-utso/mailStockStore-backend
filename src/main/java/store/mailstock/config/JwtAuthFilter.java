package store.mailstock.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import store.mailstock.auth.entity.User;
import store.mailstock.auth.service.CustomUserDetailsService;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    /** Endpoints a must-change-password account may still call — everything else is blocked. */
    private static boolean allowedWhilePasswordChangeRequired(String uri) {
        return uri.equals("/api/profile/change-password")
                || uri.equals("/api/profile/me")
                || uri.equals("/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res); return;
        }
        String token = header.substring(7);
        try {
            String username = jwtService.extractUsername(token);
            String type = jwtService.extractType(token);
            if (username != null && "access".equals(type)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (jwtService.isValid(token, user)) {
                    // Hard server-side gate: an account with a forced password reset (e.g. the leaked
                    // seed-admin default) can do NOTHING except change its password until it does —
                    // this can't be bypassed by ignoring a frontend redirect, unlike a client-only check.
                    if (user instanceof User u && u.isMustChangePassword()
                            && !allowedWhilePasswordChangeRequired(req.getRequestURI())) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"success\":false,\"message\":\"You must change your password before continuing.\"}");
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception ignored) { /* invalid token → anonymous */ }
        chain.doFilter(req, res);
    }
}
