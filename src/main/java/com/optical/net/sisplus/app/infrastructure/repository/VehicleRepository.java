package com.optical.net.sisplus.app.infrastructure.repository;

import com.optical.net.sisplus.app.infrastructure.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByActiveTrueOrderByNameAsc();

    long countByDriverId(Long driverId);

    java.util.Optional<Vehicle> findByPlateIgnoreCase(String plate);
}
