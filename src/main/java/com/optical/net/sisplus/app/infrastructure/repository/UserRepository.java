package com.optical.net.sisplus.app.infrastructure.repository;

import com.optical.net.sisplus.app.infrastructure.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    void removeById(Long id);

    /**
     * SOLUCIÓN N+1: Encuentra un usuario con sus asistencias en una sola query
     * Usa LEFT JOIN FETCH para cargar eagerly la relación
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.attendances WHERE u.id = :id")
    Optional<User> findByIdWithAttendances(@Param("id") Long id);

    /**
     * OPTIMIZACIÓN: Encuentra todos los usuarios SIN cargar asistencias
     * Perfecto para listados donde no se necesitan las asistencias
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.attendances")
    java.util.List<User> findAllWithoutAttendances();

    Optional<User> findByCc(String cc);

    Optional<User> findByZkPin(String zkPin);
}