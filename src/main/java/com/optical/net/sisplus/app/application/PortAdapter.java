package com.optical.net.sisplus.app.application;

import com.optical.net.sisplus.app.domain.AdminDomain;
import com.optical.net.sisplus.app.domain.AttendanceDomain;
import com.optical.net.sisplus.app.domain.ConfigurationDomain;
import com.optical.net.sisplus.app.domain.UserDomain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PortAdapter {
    UserDomain saveUser(UserDomain userDomain);

    UserDomain findUserById(Long usuarioId);

    UserDomain findUserByZkPin(String zkPin);

    List<UserDomain> getAllUsers();

    void deleteUser(Long id);

    void registerAttendance(Long usuarioId);
    void registerAttendance(Long usuarioId, Double latitude, Double longitude);

    void registerAttendanceByCc(String cc);
    void registerAttendanceByCc(String cc, Double latitude, Double longitude);

    void registerDeparture(Long usuarioId);
    void registerDeparture(Long usuarioId, Double latitude, Double longitude);
    void registerDeparture(String cc);
    void registerDeparture(String cc, Double latitude, Double longitude);

    AttendanceDomain getAttendanceForDay(Long usuarioId, LocalDate fecha);

    AttendanceDomain getAttendanceById(Long attendanceId);

    List<AttendanceDomain> getAttendancesForDay(LocalDate fecha);

    List<AttendanceDomain> getAllAttendances();

    List<AttendanceDomain> getAttendancesByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    List<AttendanceDomain> getAttendancesByUserAndDateRange(Long usuarioId, LocalDateTime startDate, LocalDateTime endDate);

    AttendanceDomain updateAttendance(Long attendanceId, LocalDateTime entryTime, LocalDateTime departureTime);

    void deleteAttendance(Long attendanceId);

    ConfigurationDomain saveConfig(ConfigurationDomain config);

    ConfigurationDomain getConfig(String key);

    List<ConfigurationDomain> getAllConfig();

    ConfigurationDomain updateConfig(String key, String value);

    AdminDomain save(AdminDomain adminDomain);

    AdminDomain findByUsername(String username);

    boolean removeAdmin(String username);

    List<AdminDomain> findAllAdmins();
}