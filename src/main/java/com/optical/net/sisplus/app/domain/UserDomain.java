package com.optical.net.sisplus.app.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
@Getter
@Setter
public class UserDomain {
    private Long id;
    private String name;
    private String lastName;
    private String cc;
    private String zkPin;
    private double salary;
    private List<AttendanceDomain> attendance;

    public PayrollCalculation calculateDailyPayroll(LocalDate date, PayrollConfiguration config) {
        if (attendance == null || attendance.isEmpty()) {
            return PayrollCalculation.builder().build();
        }

        AttendanceDomain todayAttendance = getAttendanceByDate(date);
        if (todayAttendance == null || !todayAttendance.isComplete()) {
            return PayrollCalculation.builder().build();
        }

        double hourlyRate = salary > 0 ? salary / config.getWorkHoursPerMonth() : 0;

        double workedHours = todayAttendance.getWorkedHours();
        double regularHours = Math.min(workedHours, config.getRegularWorkHours());
        double dayOvertimeHours = calculateDayOvertimeHoursFromAttendance(todayAttendance, config);
        double nightOvertimeHours = calculateNightOvertimeHoursFromAttendance(todayAttendance, config);
        double nightHours = todayAttendance.getNightHours(config);

        double regularPay = regularHours * hourlyRate;
        double nightSurchargePay = nightHours * hourlyRate * config.getNightSurchargeMultiplier();
        double dayOvertimePay = dayOvertimeHours * hourlyRate * config.getDayOvertimeMultiplier();
        double nightOvertimePay = nightOvertimeHours * hourlyRate * config.getNightOvertimeMultiplier();
        double totalOvertimePay = dayOvertimePay + nightOvertimePay;
        double totalPay = regularPay + nightSurchargePay + totalOvertimePay;

        return PayrollCalculation.builder()
                .regularHours(regularHours)
                .dayOvertimeHours(dayOvertimeHours)
                .nightOvertimeHours(nightOvertimeHours)
                .nightHours(nightHours)
                .regularPay(regularPay)
                .nightSurchargePay(nightSurchargePay)
                .dayOvertimePay(dayOvertimePay)
                .nightOvertimePay(nightOvertimePay)
                .totalOvertimePay(totalOvertimePay)
                .totalPay(totalPay)
                .build();
    }

    public PayrollCalculation calculateWeeklyPayroll(LocalDate date, PayrollConfiguration config) {
        if (attendance == null || attendance.isEmpty()) {
            return PayrollCalculation.builder().build();
        }

        LocalDate weekStart = date.minusDays(6);
        List<AttendanceDomain> weeklyAttendances = attendance.stream()
                .filter(a -> a.getDate() != null)
                .filter(a -> !a.getDate().isBefore(weekStart) && !a.getDate().isAfter(date))
                .filter(AttendanceDomain::isComplete)
                .toList();

        return calculatePayrollForPeriod(weeklyAttendances, config);
    }

    public PayrollCalculation calculateMonthlyPayroll(int month, int year, PayrollConfiguration config) {
        if (attendance == null || attendance.isEmpty()) {
            return PayrollCalculation.builder()
                    .regularPay(salary > 0 ? salary : 0)
                    .totalPay(salary > 0 ? salary : 0)
                    .build();
        }

        List<AttendanceDomain> monthlyAttendances = attendance.stream()
                .filter(a -> a.getDate() != null)
                .filter(a -> a.getDate().getMonthValue() == month && a.getDate().getYear() == year)
                .filter(AttendanceDomain::isComplete)
                .toList();

        double hourlyRate = salary > 0 ? salary / config.getWorkHoursPerMonth() : 0;

        double totalRegularHours = 0;
        double totalDayOvertimeHours = 0;
        double totalNightOvertimeHours = 0;
        double totalNightHours = 0;
        double totalNightSurchargePay = 0;
        double totalDayOvertimePay = 0;
        double totalNightOvertimePay = 0;

        for (AttendanceDomain att : monthlyAttendances) {
            if (att == null || !att.isComplete()) {
                continue;
            }

            double workedHours = att.getWorkedHours();
            double regularHours = Math.min(workedHours, config.getRegularWorkHours());
            double nightHours = att.getNightHours(config);
            double dayOvertimeHours = calculateDayOvertimeHoursFromAttendance(att, config);
            double nightOvertimeHours = calculateNightOvertimeHoursFromAttendance(att, config);

            totalRegularHours += regularHours;
            totalDayOvertimeHours += dayOvertimeHours;
            totalNightOvertimeHours += nightOvertimeHours;
            totalNightHours += nightHours;

            totalNightSurchargePay += nightHours * hourlyRate * config.getNightSurchargeMultiplier();
            totalDayOvertimePay += dayOvertimeHours * hourlyRate * config.getDayOvertimeMultiplier();
            totalNightOvertimePay += nightOvertimeHours * hourlyRate * config.getNightOvertimeMultiplier();
        }

        double basePay = salary > 0 ? salary : totalRegularHours * hourlyRate;
        double totalOvertimePay = totalDayOvertimePay + totalNightOvertimePay;
        double totalPay = basePay + totalNightSurchargePay + totalOvertimePay;

        return PayrollCalculation.builder()
                .regularHours(totalRegularHours)
                .dayOvertimeHours(totalDayOvertimeHours)
                .nightOvertimeHours(totalNightOvertimeHours)
                .nightHours(totalNightHours)
                .regularPay(basePay)
                .nightSurchargePay(totalNightSurchargePay)
                .dayOvertimePay(totalDayOvertimePay)
                .nightOvertimePay(totalNightOvertimePay)
                .totalOvertimePay(totalOvertimePay)
                .totalPay(totalPay)
                .build();
    }

    private PayrollCalculation calculatePayrollForPeriod(List<AttendanceDomain> attendances, PayrollConfiguration config) {
        double hourlyRate = salary > 0 ? salary / config.getWorkHoursPerMonth() : 0;

        double totalRegularHours = 0;
        double totalDayOvertimeHours = 0;
        double totalNightOvertimeHours = 0;
        double totalNightHours = 0;
        double totalRegularPay = 0;
        double totalNightSurchargePay = 0;
        double totalDayOvertimePay = 0;
        double totalNightOvertimePay = 0;

        for (AttendanceDomain att : attendances) {
            if (att == null || !att.isComplete()) {
                continue;
            }

            double workedHours = att.getWorkedHours();
            double regularHours = Math.min(workedHours, config.getRegularWorkHours());
            double nightHours = att.getNightHours(config);
            double dayOvertimeHours = calculateDayOvertimeHoursFromAttendance(att, config);
            double nightOvertimeHours = calculateNightOvertimeHoursFromAttendance(att, config);

            totalRegularHours += regularHours;
            totalDayOvertimeHours += dayOvertimeHours;
            totalNightOvertimeHours += nightOvertimeHours;
            totalNightHours += nightHours;

            totalRegularPay += regularHours * hourlyRate;
            totalNightSurchargePay += nightHours * hourlyRate * config.getNightSurchargeMultiplier();
            totalDayOvertimePay += dayOvertimeHours * hourlyRate * config.getDayOvertimeMultiplier();
            totalNightOvertimePay += nightOvertimeHours * hourlyRate * config.getNightOvertimeMultiplier();
        }

        double totalOvertimePay = totalDayOvertimePay + totalNightOvertimePay;
        double totalPay = totalRegularPay + totalNightSurchargePay + totalOvertimePay;

        return PayrollCalculation.builder()
                .regularHours(totalRegularHours)
                .dayOvertimeHours(totalDayOvertimeHours)
                .nightOvertimeHours(totalNightOvertimeHours)
                .nightHours(totalNightHours)
                .regularPay(totalRegularPay)
                .nightSurchargePay(totalNightSurchargePay)
                .dayOvertimePay(totalDayOvertimePay)
                .nightOvertimePay(totalNightOvertimePay)
                .totalOvertimePay(totalOvertimePay)
                .totalPay(totalPay)
                .build();
    }

    private double calculateDayOvertimeHoursFromAttendance(AttendanceDomain attendance, PayrollConfiguration config) {
        if (attendance == null || !attendance.isComplete()) {
            return 0.0;
        }

        double workedHours = attendance.getWorkedHours();
        if (workedHours <= config.getRegularWorkHours()) {
            return 0.0;
        }

        LocalDateTime entryTime = attendance.getEntryTime();
        LocalDateTime departureTime = attendance.getDepartureTime();
        LocalDateTime regularWorkEnd = entryTime.plusMinutes((long)(config.getRegularWorkHours() * 60));

        if (!regularWorkEnd.isBefore(departureTime)) {
            return 0.0;
        }

        long dayMinutes = 0;
        LocalDateTime current = regularWorkEnd;

        while (current.isBefore(departureTime)) {
            int hour = current.getHour();
            if (hour >= config.getNightEndHour() && hour < config.getNightStartHour()) {
                dayMinutes++;
            }
            current = current.plusMinutes(1);
        }

        return dayMinutes / 60.0;
    }

    private double calculateNightOvertimeHoursFromAttendance(AttendanceDomain attendance, PayrollConfiguration config) {
        if (attendance == null || !attendance.isComplete()) {
            return 0.0;
        }

        double workedHours = attendance.getWorkedHours();
        if (workedHours <= config.getRegularWorkHours()) {
            return 0.0;
        }

        LocalDateTime entryTime = attendance.getEntryTime();
        LocalDateTime departureTime = attendance.getDepartureTime();
        LocalDateTime regularWorkEnd = entryTime.plusMinutes((long)(config.getRegularWorkHours() * 60));

        if (!regularWorkEnd.isBefore(departureTime)) {
            return 0.0;
        }

        long nightMinutes = 0;
        LocalDateTime current = regularWorkEnd;

        while (current.isBefore(departureTime)) {
            int hour = current.getHour();
            if (hour >= config.getNightStartHour() || hour < config.getNightEndHour()) {
                nightMinutes++;
            }
            current = current.plusMinutes(1);
        }

        return nightMinutes / 60.0;
    }

    private AttendanceDomain getAttendanceByDate(LocalDate date) {
        if (attendance == null || attendance.isEmpty()) {
            return null;
        }
        return attendance.stream()
                .filter(a -> date.equals(a.getDate()))
                .findFirst()
                .orElse(null);
    }

    @Deprecated(since = "1.1", forRemoval = true)
    public PayrollCalculation calculateDailyPayroll(LocalDate date) {
        return calculateDailyPayroll(date, PayrollConfiguration.defaults());
    }

    @Deprecated(since = "1.1", forRemoval = true)
    public PayrollCalculation calculateWeeklyPayroll(LocalDate date) {
        return calculateWeeklyPayroll(date, PayrollConfiguration.defaults());
    }

    @Deprecated(since = "1.1", forRemoval = true)
    public PayrollCalculation calculateMonthlyPayroll(int month, int year) {
        return calculateMonthlyPayroll(month, year, PayrollConfiguration.defaults());
    }
}