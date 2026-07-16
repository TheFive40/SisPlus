package com.optical.net.sisplus.app.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Builder
@Getter
@Setter
public class AdminDomain {
    private Long id;
    private String username;
    private String password;
    private Set<String> roles;
}
