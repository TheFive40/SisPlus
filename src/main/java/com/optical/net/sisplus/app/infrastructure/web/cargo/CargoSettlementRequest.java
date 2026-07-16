package com.optical.net.sisplus.app.infrastructure.web.cargo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CargoSettlementRequest {
    private Long cargoLoadId;
    private Double deliveredValue;
    private Double returnedValue;
    private Double coins;
    private Double cash;
    private Double qr;
    private Double security;
    private Double expense;
}
