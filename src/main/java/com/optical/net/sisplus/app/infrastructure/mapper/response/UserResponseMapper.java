package com.optical.net.sisplus.app.infrastructure.mapper.response;

import com.optical.net.sisplus.app.domain.PayrollCalculation;
import com.optical.net.sisplus.app.domain.UserDomain;
import com.optical.net.sisplus.app.infrastructure.web.UserResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class UserResponseMapper {

    public static UserResponse fromDomainWithPayroll(
            UserDomain user,
            LocalDate date,
            Integer month,
            Integer year,
            String period
    ) {
        PayrollCalculation payroll = calculatePayrollByPeriod(user, date, month, year, period);
        return buildUserResponseWithPayroll(user, payroll);
    }

    public static UserResponse fromDomainWithPayroll(
            UserDomain user,
            PayrollCalculation payroll
    ) {
        return buildUserResponseWithPayroll(user, payroll);
    }

    private static UserResponse buildUserResponseWithPayroll(
            UserDomain user,
            PayrollCalculation payroll
    ) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .lastName(user.getLastName())
                .cc(user.getCc())
                .zkPin(user.getZkPin())
                .salary(user.getSalary())
                .regularHours(payroll.getRegularHours())
                .dayOvertimeHours(payroll.getDayOvertimeHours())
                .nightOvertimeHours(payroll.getNightOvertimeHours())
                .nightHours(payroll.getNightHours())
                .regularPay(payroll.getRegularPay())
                .nightSurchargePay(payroll.getNightSurchargePay())
                .dayOvertimePay(payroll.getDayOvertimePay())
                .nightOvertimePay(payroll.getNightOvertimePay())
                .totalOvertimePay(payroll.getTotalOvertimePay())
                .totalPay(payroll.getTotalPay())
                .build();
    }

    public static UserResponse toBasicUserResponse(UserDomain user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .lastName(user.getLastName())
                .cc(user.getCc())
                .zkPin(user.getZkPin())
                .salary(user.getSalary())
                .build();
    }

    public static List<UserResponse> toBasicUserResponseList(List<UserDomain> users) {
        return users.stream()
                .map(UserResponseMapper::toBasicUserResponse)
                .collect(Collectors.toList());
    }

    private static PayrollCalculation calculatePayrollByPeriod(
            UserDomain user,
            LocalDate date,
            Integer month,
            Integer year,
            String period
    ) {
        if (period != null) {
            return switch (period.toLowerCase()) {
                case "weekly" -> user.calculateWeeklyPayroll(date != null ? date : LocalDate.now());
                case "monthly" -> {
                    int m = month != null ? month : LocalDate.now().getMonthValue();
                    int y = year != null ? year : LocalDate.now().getYear();
                    yield user.calculateMonthlyPayroll(m, y);
                }
                default -> user.calculateDailyPayroll(date != null ? date : LocalDate.now());
            };
        }

        if (month != null && year != null) {
            return user.calculateMonthlyPayroll(month, year);
        }

        return user.calculateDailyPayroll(date != null ? date : LocalDate.now());
    }
}