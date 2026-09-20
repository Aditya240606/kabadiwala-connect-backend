package com.kabadiwala.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final UserRole role;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, UserRole role) {
        this.id = id;
        this.email = email;
        this.role = role != null ? role : UserRole.COLLECTOR;
        this.authorities = List.of(new SimpleGrantedAuthority(this.role.getAuthority()));
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // Managed by Supabase
    }

    @Override
    public String getUsername() {
        return email != null ? email : id.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
