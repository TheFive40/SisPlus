package com.optical.net.sisplus.app.infrastructure.web;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String lastName;
    private String cc;
    private String zkPin;
    private double salary;

    private double regularHours;
    private double dayOvertimeHours;
    private double nightOvertimeHours;
    private double nightHours;

    private double regularPay;
    private double nightSurchargePay;
    private double dayOvertimePay;
    private double nightOvertimePay;
    private double totalOvertimePay;
    private double totalPay;
}