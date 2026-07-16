package com.optical.net.sisplus.app.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String lastName;

    @Column(unique = true, nullable = false)
    private String cc;

    @Column(name = "zk_pin", unique = true)
    private String zkPin;

    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean status;

    @Column(nullable = false)
    @Builder.Default
    private double salary = 2000_000;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<Attendance> attendances;
}