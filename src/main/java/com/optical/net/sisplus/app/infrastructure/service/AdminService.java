package com.optical.net.sisplus.app.infrastructure.service;

import com.optical.net.sisplus.app.domain.AdminDomain;
import com.optical.net.sisplus.app.infrastructure.adapter.PortCaseAdapter;
import com.optical.net.sisplus.app.infrastructure.entity.Admin;
import com.optical.net.sisplus.app.infrastructure.repository.AdminRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminService {
    private final PortCaseAdapter portCaseAdapter;
    private final PasswordEncoder passwordEncoder;
    private final AdminRepository adminRepository;

    public AdminService(PortCaseAdapter portCaseAdapter, PasswordEncoder passwordEncoder, AdminRepository adminRepository) {
        this.portCaseAdapter = portCaseAdapter;
        this.passwordEncoder = passwordEncoder;
        this.adminRepository = adminRepository;
    }

    public AdminDomain save(AdminDomain adminDomain) {
        if (adminDomain.getPassword() != null && !adminDomain.getPassword().isEmpty()) {
            adminDomain.setPassword(passwordEncoder.encode(adminDomain.getPassword()));
        }
        if (adminDomain.getRoles() == null || adminDomain.getRoles().isEmpty()) {
            Set<String> defaultRoles = new HashSet<>();
            defaultRoles.add("ADMIN");
            adminDomain.setRoles(defaultRoles);
        }
        return portCaseAdapter.save(adminDomain);
    }

    @Transactional
    public void initializeDefaultAdmin() {
        if (!adminRepository.existsByUsername("admin")) {
            Admin defaultAdmin = new Admin();
            defaultAdmin.setUsername("admin");
            defaultAdmin.setPassword(passwordEncoder.encode("admin123"));
            defaultAdmin.setEnabled(true);
            defaultAdmin.setAccountNonExpired(true);
            defaultAdmin.setAccountNonLocked(true);
            defaultAdmin.setCredentialsNonExpired(true);

            Set<String> roles = new HashSet<>();
            roles.add("ADMIN");
            defaultAdmin.setRoles(roles);

            adminRepository.save(defaultAdmin);
            System.out.println("✅ Administrador por defecto creado - Username: admin, Password: admin123");
        } else {
            System.out.println("ℹ️  El administrador por defecto ya existe");
        }
    }
    public AdminDomain findByUsername(String username) {
        return portCaseAdapter.findByUsername(username);
    }

    public boolean removeAdmin(String username) {
        return portCaseAdapter.removeAdmin(username);
    }

    public List<AdminDomain> findAllAdmins() {
        return portCaseAdapter.findAllAdmins();
    }

    @Transactional
    public boolean assignRole(String username, String role) {
        Admin admin = adminRepository.findByUsername(username);
        if (admin == null) {
            return false;
        }
        if (admin.getRoles() == null) {
            admin.setRoles(new HashSet<>());
        }
        admin.getRoles().add(role.toUpperCase());
        adminRepository.save(admin);
        return true;
    }

    @Transactional
    public boolean removeRole(String username, String role) {
        Admin admin = adminRepository.findByUsername(username);
        if (admin == null || admin.getRoles() == null) {
            return false;
        }
        admin.getRoles().remove(role.toUpperCase());
        adminRepository.save(admin);
        return true;
    }

    public void createDefaultAdminIfNotExists() {
        if (!adminRepository.existsByUsername("admin")) {
            Admin defaultAdmin = new Admin();
            defaultAdmin.setUsername("admin");
            defaultAdmin.setPassword(passwordEncoder.encode("admin123"));
            defaultAdmin.setEnabled(true);
            defaultAdmin.setAccountNonExpired(true);
            defaultAdmin.setAccountNonLocked(true);
            defaultAdmin.setCredentialsNonExpired(true);

            Set<String> roles = new HashSet<>();
            roles.add("ADMIN");
            defaultAdmin.setRoles(roles);

            adminRepository.save(defaultAdmin);
            System.out.println("✅ Administrador por defecto creado - Username: admin, Password: admin123");
        } else {
            System.out.println("ℹ️  El administrador por defecto ya existe");
        }
    }
}