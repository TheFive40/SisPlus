package com.optical.net.sisplus.app.infrastructure.mapper.domains;

import com.optical.net.sisplus.app.domain.AttendanceDomain;
import com.optical.net.sisplus.app.domain.UserDomain;
import com.optical.net.sisplus.app.infrastructure.entity.Attendance;
import com.optical.net.sisplus.app.infrastructure.entity.User;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {AttendanceMapper.class})
public abstract class UserMapper {

    @Autowired
    protected AttendanceMapper attendanceMapper;

    public UserDomain toDomain(User user) {
        if (user == null) {
            return null;
        }

        List<AttendanceDomain> attendanceList = new ArrayList<>();
        if (user.getAttendances() != null) {
            attendanceList = user.getAttendances().stream()
                    .map(attendanceMapper::toDomain)
                    .collect(Collectors.toList());
        }

        return UserDomain.builder()
                .id(user.getId())
                .name(user.getName())
                .lastName(user.getLastName())
                .cc(user.getCc())
                .zkPin(user.getZkPin())
                .salary(user.getSalary())
                .attendance(attendanceList)
                .build();
    }

    public User toEntity(UserDomain domain) {
        if (domain == null) {
            return null;
        }

        List<Attendance> attendanceList = new ArrayList<>();
        if (domain.getAttendance() != null) {
            attendanceList = domain.getAttendance().stream()
                    .map(attendanceMapper::toEntity)
                    .collect(Collectors.toList());
        }

        return User.builder()
                .id(domain.getId())
                .name(domain.getName())
                .lastName(domain.getLastName())
                .cc(domain.getCc())
                .zkPin(domain.getZkPin())
                .salary(domain.getSalary() > 0 ? domain.getSalary() : 1_423_500.0)
                .attendances(attendanceList)
                .build();
    }
}