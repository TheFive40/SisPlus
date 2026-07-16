package com.optical.net.sisplus.app.infrastructure.controller.api;

import com.optical.net.sisplus.app.infrastructure.mapper.response.AdminResponseMapper;
import com.optical.net.sisplus.app.infrastructure.service.AdminService;
import com.optical.net.sisplus.app.infrastructure.web.AdminRequest;
import com.optical.net.sisplus.app.infrastructure.web.AdminResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminResponseMapper adminResponseMapper;
    private final AdminService adminService;

    public AdminController(AdminResponseMapper adminResponseMapper, AdminService adminService) {
        this.adminResponseMapper = adminResponseMapper;
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<String> register(@RequestBody AdminRequest request) {
        adminService.save(adminResponseMapper.fromRequest(request));
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/")
    public List<AdminResponse> findAll() {
        return adminResponseMapper.fromDomains(adminService.findAllAdmins());
    }
    @GetMapping("/{username}")
    public AdminResponse findByUsername(@PathVariable String username) {
        return adminResponseMapper.fromDomain(adminService.findByUsername(username));
    }
    @DeleteMapping("/{username}")
    public ResponseEntity<String> remove(@PathVariable String username) {
        adminService.removeAdmin(username);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{username}/roles")
    public ResponseEntity<String> assignRole(
            @PathVariable String username,
            @RequestParam String role
    ) {
        boolean assigned = adminService.assignRole(username, role);
        return assigned ? ResponseEntity.ok("Rol asignado") : ResponseEntity.badRequest().body("Usuario no encontrado");
    }

    @DeleteMapping("/{username}/roles")
    public ResponseEntity<String> removeRole(
            @PathVariable String username,
            @RequestParam String role
    ) {
        boolean removed = adminService.removeRole(username, role);
        return removed ? ResponseEntity.ok("Rol eliminado") : ResponseEntity.badRequest().body("Usuario no encontrado");
    }

}
