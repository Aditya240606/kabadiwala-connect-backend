package com.kabadiwala.backend.auth;

public enum UserRole {
    COLLECTOR,
    RECYCLER,
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
