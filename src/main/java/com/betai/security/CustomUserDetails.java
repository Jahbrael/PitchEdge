package com.betai.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

public class CustomUserDetails extends User {
    private final UUID id;

    public CustomUserDetails(UUID id, String username, String password, boolean enabled, boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
