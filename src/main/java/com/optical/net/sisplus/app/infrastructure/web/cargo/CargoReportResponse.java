package com.optical.net.sisplus.app.infrastructure.web.cargo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class CargoReportResponse {
    private LocalDate date;
    private List<CargoLoadResponse> loads;
    private Double totalMerchandise;
    private Double totalDelivered;
    private Double totalReturned;
    private Double totalCoins;
    private Double totalCash;
    private Double totalQr;
    private Double totalSecurity;
    private Double totalExpense;
    private Double grandTotal;
    private Long deliveredCount;
    private Long pendingCount;
    private Double totalExpenses;
    private Map<String, Double> expensesByCategory;
}
