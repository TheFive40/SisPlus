package com.optical.net.sisplus.app.infrastructure.web.cargo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CargoSettlementResponse {
    private Long id;
    private Double deliveredValue;
    private Double returnedValue;
    private Double coins;
    private Double cash;
    private Double qr;
    private Double security;
    private Double expense;
    private Double total;
    private LocalDateTime settlementDate;
}
