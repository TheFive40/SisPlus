package com.optical.net.sisplus.app.infrastructure.web;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
public class AuthResponse {
    private boolean success;
    private String message;
    private String username;
    private String redirectUrl;
    private Set<String> roles;
}