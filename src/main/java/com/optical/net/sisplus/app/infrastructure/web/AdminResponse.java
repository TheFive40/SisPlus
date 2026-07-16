package com.optical.net.sisplus.app.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Builder
@Getter
@Setter
public class AdminResponse {
    private Long id;
    private String username;

    @JsonIgnore
    private String password;

    private Set<String> roles;
}