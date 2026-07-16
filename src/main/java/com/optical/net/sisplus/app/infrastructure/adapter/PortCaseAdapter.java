package com.optical.net.sisplus.app.infrastructure.adapter;

import com.optical.net.sisplus.app.application.PortAdapter;
import com.optical.net.sisplus.app.domain.AdminDomain;
import com.optical.net.sisplus.app.domain.AttendanceDomain;
import com.optical.net.sisplus.app.domain.ConfigurationDomain;
import com.optical.net.sisplus.app.domain.UserDomain;
import com.optical.net.sisplus.app.infrastructure.entity.Attendance;
import com.optical.net.sisplus.app.infrastructure.entity.Configuration;
import com.optical.net.sisplus.app.infrastructure.mapper.domains.AdminMapper;
import com.optical.net.sisplus.app.infrastructure.mapper.domains.AttendanceMapper;
import com.optical.net.sisplus.app.infrastructure.mapper.domains.UserMapper;
import com.optical.net.sisplus.app.infrastructure.repository.AdminRepository;
import com.optical.net.sisplus.app.infrastructure.repository.AttendanceRepository;
import com.optical.net.sisplus.app.infrastructure.repository.ConfigurationRepository;
import com.optical.net.sisplus.app.infrastructure.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class PortCaseAdapter implements PortAdapter {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;
    private final ConfigurationRepository configurationRepository;
    private final AdminRepository adminRepository;
    private final AdminMapper adminMapper;

    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    public PortCaseAdapter(UserRepository userRepository, UserMapper userMapper,
                           AttendanceRepository attendanceRepository, AttendanceMapper attendanceMapper,
                           ConfigurationRepository configurationRepository, AdminRepository adminRepository,
                           AdminMapper adminMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.attendanceRepository = attendanceRepository;
        this.attendanceMapper = attendanceMapper;
        this.configurationRepository = configurationRepository;
        this.adminRepository = adminRepository;
        this.adminMapper = adminMapper;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", allEntries = true),
            @CacheEvict(value = "userById", allEntries = true),
            @CacheEvict(value = "payrollCalculations", allEntries = true)
    })
    public UserDomain saveUser(UserDomain userDomain) {
        log.debug("Guardando usuario: {}", userDomain.getCc());
        var savedUser = userRepository.save(userMapper.toEntity(userDomain));
        return userMapper.toDomain(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDomain findUserById(Long usuarioId) {
        log.debug("Buscando usuario por ID: {}", usuarioId);

        var user = userRepository.findByIdWithAttendances(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));

        var userDomain = UserDomain.builder()
                .id(user.getId())
                .name(user.getName())
                .lastName(user.getLastName())
                .cc(user.getCc())
                .salary(user.getSalary())
                .build();

        List<AttendanceDomain> attendanceDomains = new ArrayList<>();
        if (user.getAttendances() != null) {
            for (var att : user.getAttendances()) {
                AttendanceDomain attDomain = AttendanceDomain.builder()
                        .id(att.getId())
                        .entryTime(att.getEntryTime())
                        .departureTime(att.getDepartureTime())
                        .build();
                attDomain.setUser(userDomain);
                attendanceDomains.add(attDomain);
            }
        }

        userDomain.setAttendance(attendanceDomains);

        log.debug("Usuario {} cargado con {} asistencias", usuarioId, attendanceDomains.size());

        return userDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDomain findUserByZkPin(String zkPin) {
        log.debug("Buscando usuario por PIN de huellero: {}", zkPin);
        var user = userRepository.findByZkPin(zkPin)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con PIN de huellero: " + zkPin));
        return userMapper.toDomain(user);
    }

    @Override
    @Cacheable(value = "users")
    @Transactional(readOnly = true)
    public List<UserDomain> getAllUsers() {
        log.debug("Obteniendo todos los usuarios (con caché)");
        return userRepository.findAllWithoutAttendances()
                .stream()
                .map(user -> {
                    var userDomain = userMapper.toDomain(user);
                    userDomain.setAttendance(new ArrayList<>());
                    return userDomain;
                })
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users", allEntries = true),
            @CacheEvict(value = "userById", key = "#id"),
            @CacheEvict(value = "payrollCalculations", allEntries = true)
    })
    public void deleteUser(Long id) {
        log.info("Eliminando usuario con ID: {}", id);
        userRepository.removeById(id);
    }

    @Override
    public void registerAttendance(Long usuarioId) {
        registerAttendance(usuarioId, null, null);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "todayAttendances", allEntries = true),
            @CacheEvict(value = "userById", allEntries = true),
            @CacheEvict(value = "payrollCalculations", allEntries = true)
    })
    public void registerAttendance(Long usuarioId, Double latitude, Double longitude) {
        log.info("Registrando asistencia para usuario: {} con ubicación: {}, {}", usuarioId, latitude, longitude);

        var user = userRepository.findById(usuarioId).orElseThrow(
                () -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId)
        );

        LocalDateTime ahora = LocalDateTime.now(COLOMBIA_ZONE);

        var attendances = attendanceRepository.findByUser(user);
        boolean yaRegistroHoy = attendances.stream()
                .anyMatch(att -> att.getEntryTime() != null &&
                        att.getEntryTime().toLocalDate().equals(ahora.toLocalDate()));

        if (yaRegistroHoy) {
            registerDeparture(usuarioId, latitude, longitude);
            return;
        }

        Attendance attendance = Attendance.builder()
                .user(user)
                .entryTime(ahora)
                .entryLatitude(latitude)
                .entryLongitude(longitude)
                .build();

        attendanceRepository.save(attendance);
    }

    @Override
    public void registerAttendanceByCc(String cc) {
        registerAttendanceByCc(cc, null, null);
    }

    @Override
    public void registerAttendanceByCc(String cc, Double latitude, Double longitude) {
        log.info("Registrando asistencia para usuario con CC: {} con ubicación: {}, {}", cc, latitude, longitude);
        var user = userRepository.findByCc(cc).orElseThrow(
                () -> new RuntimeException("Usuario no encontrado con CC: " + cc)
        );
        registerAttendance(user.getId(), latitude, longitude);
    }

    @Override
    public void registerDeparture(Long usuarioId) {
        registerDeparture(usuarioId, null, null);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "todayAttendances", allEntries = true),
            @CacheEvict(value = "userById", allEntries = true),
            @CacheEvict(value = "payrollCalculations", allEntries = true)
    })
    public void registerDeparture(Long usuarioId, Double latitude, Double longitude) {
        log.info("Registrando salida para usuario: {} con ubicación: {}, {}", usuarioId, latitude, longitude);

        var user = userRepository.findById(usuarioId).orElseThrow(
                () -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId)
        );

        LocalDateTime ahora = LocalDateTime.now(COLOMBIA_ZONE);

        var attendances = attendanceRepository.findByUser(user);

        var attendance = attendances.stream()
                .filter(e -> e.getEntryTime() != null &&
                        e.getEntryTime().toLocalDate().equals(ahora.toLocalDate()) &&
                        e.getDepartureTime() == null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró un registro de entrada sin salida para hoy"
                ));

        attendance.setDepartureTime(ahora);
        attendance.setExitLatitude(latitude);
        attendance.setExitLongitude(longitude);
        attendanceRepository.save(attendance);
    }

    @Override
    public void registerDeparture(String cc) {
        registerDeparture(cc, null, null);
    }

    @Override
    public void registerDeparture(String cc, Double latitude, Double longitude) {
        log.info("Registrando salida para usuario: {} con ubicación: {}, {}", cc, latitude, longitude);

        var user = userRepository.findByCc(cc).orElseThrow(
                () -> new RuntimeException("Usuario no encontrado con ID: " + cc)
        );
        LocalDateTime ahora = LocalDateTime.now(COLOMBIA_ZONE);

        var attendances = attendanceRepository.findByUser(user);
        var attendance = attendances.stream()
                .filter(e -> e.getEntryTime() != null &&
                        e.getEntryTime().toLocalDate().equals(ahora.toLocalDate()) &&
                        e.getDepartureTime() == null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró un registro de entrada sin salida para hoy"
                ));
        attendance.setDepartureTime(ahora);
        attendance.setExitLatitude(latitude);
        attendance.setExitLongitude(longitude);
        attendanceRepository.save(attendance);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceDomain getAttendanceForDay(Long usuarioId, LocalDate fecha) {
        var user = userRepository.findById(usuarioId).orElseThrow(
                () -> new RuntimeException("Usuario no encontrado")
        );

        var attendances = attendanceRepository.findByUser(user);

        return attendances.stream()
                .filter(att -> att.getEntryTime() != null &&
                        att.getEntryTime().toLocalDate().equals(fecha))
                .findFirst()
                .map(attendanceMapper::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceDomain getAttendanceById(Long attendanceId) {
        var attendance = attendanceRepository.findById(attendanceId).orElseThrow(
                () -> new RuntimeException("Asistencia no encontrada con ID: " + attendanceId)
        );
        return attendanceMapper.toDomain(attendance);
    }

    @Override
    @Cacheable(value = "todayAttendances", key = "#fecha")
    @Transactional(readOnly = true)
    public List<AttendanceDomain> getAttendancesForDay(LocalDate fecha) {
        log.debug("Obteniendo asistencias del día: {} (con caché)", fecha);

        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.plusDays(1).atStartOfDay();

        return attendanceRepository.findByDateRange(start, end)
                .stream()
                .map(attendanceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceDomain> getAllAttendances() {
        return attendanceRepository.findAll()
                .stream()
                .map(attendanceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceDomain> getAttendancesByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return attendanceRepository.findByDateRange(startDate, endDate)
                .stream()
                .map(attendanceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceDomain> getAttendancesByUserAndDateRange(Long usuarioId, LocalDateTime startDate, LocalDateTime endDate) {
        return attendanceRepository.findByUserAndDateRange(usuarioId, startDate, endDate)
                .stream()
                .map(attendanceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "todayAttendances", allEntries = true),
            @CacheEvict(value = "userById", allEntries = true),
            @CacheEvict(value = "payrollCalculations", allEntries = true)
    })
    public AttendanceDomain updateAttendance(Long attendanceId, LocalDateTime entryTime, LocalDateTime departureTime) {
        var attendance = attendanceRepository.findById(attendanceId).orElseThrow(
                () -> new RuntimeException("Asistencia no encontrada con ID: " + attendanceId)
        );

        if (entryTime != null) {
            attendance.setEntryTime(entryTime);
        }

        if (departureTime != null) {
            if (attendance.getEntryTime() != null && departureTime.isBefore(attendance.getEntryTime())) {
                throw new RuntimeException("La hora de salida no puede ser anterior a la hora de entrada");
            }
            attendance.setDepartureTime(departureTime);
        }

        var updatedAttendance = attendanceRepository.save(attendance);
        return attendanceMapper.toDomain(updatedAttendance);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "todayAttendances", allEntries = true),
            @CacheEvict(value = "userById", allEntries = true),
            @CacheEvict(value = "payrollCalculations", allEntries = true)
    })
    public void deleteAttendance(Long attendanceId) {
        if (!attendanceRepository.existsById(attendanceId)) {
            throw new RuntimeException("Asistencia no encontrada con ID: " + attendanceId);
        }
        attendanceRepository.deleteById(attendanceId);
    }

    @Override
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "payrollConfig", key = "'default'"),
                    @CacheEvict(value = "payrollConfig", key = "#config.key")
            },
            put = {
                    @CachePut(value = "payrollConfig", key = "#config.key")
            }
    )
    public ConfigurationDomain saveConfig(ConfigurationDomain config) {
        log.debug("Guardando configuración: {}", config.getKey());

        var existing = configurationRepository.findByKey(config.getKey());
        Configuration configuration;
        if (existing.isPresent()) {
            existing.get().setValue(config.getValue());
            configuration = configurationRepository.save(existing.get());
        } else {
            configuration = configurationRepository.save(Configuration.builder()
                    .key(config.getKey())
                    .value(config.getValue())
                    .build());
        }
        return ConfigurationDomain.builder()
                .id(configuration.getId())
                .key(configuration.getKey())
                .value(configuration.getValue())
                .build();
    }

    @Override
    @Cacheable(value = "payrollConfig", key = "#key")
    @Transactional(readOnly = true)
    public ConfigurationDomain getConfig(String key) {
        var config = configurationRepository.findByKey(key).orElseThrow(
                () -> new RuntimeException("Configuración no encontrada: " + key)
        );
        return ConfigurationDomain.builder()
                .value(config.getValue())
                .id(config.getId())
                .key(config.getKey())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigurationDomain> getAllConfig() {
        var entities = configurationRepository.findAll();
        return entities.stream().map(e -> ConfigurationDomain.builder()
                .id(e.getId())
                .value(e.getValue())
                .key(e.getKey())
                .build()).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "payrollConfig", allEntries = true)
    public ConfigurationDomain updateConfig(String key, String value) {
        var existing = configurationRepository.findByKey(key).orElseThrow(
                () -> new RuntimeException("Configuración no encontrada: " + key)
        );
        existing.setValue(value);
        configurationRepository.save(existing);

        return ConfigurationDomain.builder()
                .id(existing.getId())
                .key(existing.getKey())
                .value(value)
                .build();
    }

    @Override
    @Transactional
    public AdminDomain save(AdminDomain adminDomain) {
        return adminMapper.toDomain(adminRepository.save(adminMapper.toEntity(adminDomain)));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDomain findByUsername(String username) {
        return adminMapper.toDomain(adminRepository.findByUsername(username));
    }

    @Override
    @Transactional
    public boolean removeAdmin(String username) {
        adminRepository.deleteByUsername(username);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDomain> findAllAdmins() {
        return adminMapper.toDomainsList(adminRepository.findAll());
    }
}