package com.teamflow.service;

import com.teamflow.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single place that reads "who is calling right now" from the validated
 * Spring Security context. Services must obtain identity through this
 * class rather than trusting any userId/role field a client could put in
 * a request body - that is what prevents IDOR and privilege escalation.
 */
@Component
public class CurrentUser {

    public AppUserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in context.");
        }
        return principal;
    }
}
