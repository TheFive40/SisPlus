package com.optical.net.sisplus.app.infrastructure.service;

import com.optical.net.sisplus.app.infrastructure.entity.*;
import com.optical.net.sisplus.app.infrastructure.repository.*;
import com.optical.net.sisplus.app.infrastructure.web.cargo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CargoService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final CargoLoadRepository cargoLoadRepository;
    private final CargoSettlementRepository cargoSettlementRepository;
    private final CompanyExpenseService companyExpenseService;
    private final EmailService emailService;

    public CargoService(DriverRepository driverRepository,
                        VehicleRepository vehicleRepository,
                        CargoLoadRepository cargoLoadRepository,
                        CargoSettlementRepository cargoSettlementRepository,
                        CompanyExpenseService companyExpenseService,
                        EmailService emailService) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.cargoLoadRepository = cargoLoadRepository;
        this.cargoSettlementRepository = cargoSettlementRepository;
        this.companyExpenseService = companyExpenseService;
        this.emailService = emailService;
    }

    /* ── Drivers ── */
    public List<DriverResponse> getAllDrivers() {
        return driverRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toDriverResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DriverResponse createDriver(DriverRequest request) {
        Driver driver = Driver.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .active(request.getActive() == null || request.getActive())
                .build();
        return toDriverResponse(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateDriver(Long id, DriverRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conductor no encontrado"));
        driver.setName(request.getName());
        driver.setPhone(request.getPhone());
        if (request.getActive() != null) driver.setActive(request.getActive());
        return toDriverResponse(driverRepository.save(driver));
    }

    @Transactional
    public String deleteDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conductor no encontrado"));
        if (vehicleRepository.countByDriverId(id) > 0) {
            driver.setActive(false);
            driverRepository.save(driver);
            return "Conductor desactivado porque tiene carros asignados";
        }
        driverRepository.deleteById(id);
        return "Conductor eliminado";
    }

    /* ── Vehicles ── */
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toVehicleResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public VehicleResponse createVehicle(VehicleRequest request) {
        String plate = request.getPlate() != null ? request.getPlate().trim() : null;
        java.util.Optional<Vehicle> existing = vehicleRepository.findByPlateIgnoreCase(plate);
        if (existing.isPresent()) {
            Vehicle vehicle = existing.get();
            if (vehicle.isActive()) {
                throw new IllegalArgumentException("Ya existe un carro activo con la placa " + plate);
            }
            // Reactivar vehículo previamente desactivado
            Driver driver = request.getDriverId() != null
                    ? driverRepository.findById(request.getDriverId()).orElse(null)
                    : null;
            vehicle.setName(request.getName());
            vehicle.setPlate(plate);
            vehicle.setDriver(driver);
            vehicle.setActive(true);
            return toVehicleResponse(vehicleRepository.save(vehicle));
        }

        Driver driver = request.getDriverId() != null
                ? driverRepository.findById(request.getDriverId()).orElse(null)
                : null;
        Vehicle vehicle = Vehicle.builder()
                .plate(plate)
                .name(request.getName())
                .driver(driver)
                .active(true)
                .build();
        return toVehicleResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse updateVehicle(Long id, VehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehículo no encontrado"));
        vehicle.setPlate(request.getPlate());
        vehicle.setName(request.getName());
        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new RuntimeException("Conductor no encontrado"));
            vehicle.setDriver(driver);
        }
        if (request.getActive() != null) vehicle.setActive(request.getActive());
        return toVehicleResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public String deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehículo no encontrado"));
        if (cargoLoadRepository.countByVehicleId(id) > 0) {
            vehicle.setActive(false);
            vehicleRepository.save(vehicle);
            return "Vehículo desactivado porque tiene cargues registrados";
        }
        vehicleRepository.deleteById(id);
        return "Vehículo eliminado";
    }

    /* ── Cargo Loads ── */
    public List<CargoLoadResponse> getLoadsByDate(LocalDate date) {
        if (date == null) date = LocalDate.now();
        return cargoLoadRepository.findByLoadDateWithSettlement(date).stream()
                .filter(l -> l.getVehicle() != null && l.getVehicle().isActive())
                .map(this::toCargoLoadResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CargoLoadResponse createLoad(CargoLoadRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehículo no encontrado"));

        cargoLoadRepository.findByVehicleIdAndLoadDate(vehicle.getId(), request.getLoadDate())
                .ifPresent(existing -> { throw new RuntimeException("Ya existe un cargue para este carro en la fecha seleccionada"); });

        CargoLoad load = CargoLoad.builder()
                .vehicle(vehicle)
                .loadDate(request.getLoadDate())
                .merchandiseValue(request.getMerchandiseValue())
                .notes(request.getNotes())
                .build();
        return toCargoLoadResponse(cargoLoadRepository.save(load));
    }

    @Transactional
    public CargoLoadResponse updateLoad(Long id, CargoLoadRequest request) {
        CargoLoad load = cargoLoadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cargue no encontrado"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehículo no encontrado"));
        load.setVehicle(vehicle);
        load.setLoadDate(request.getLoadDate());
        load.setMerchandiseValue(request.getMerchandiseValue());
        load.setNotes(request.getNotes());
        return toCargoLoadResponse(cargoLoadRepository.save(load));
    }

    @Transactional
    public CargoLoadResponse markDelivered(Long id) {
        CargoLoad load = cargoLoadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cargue no encontrado"));
        load.setStatus(CargoStatus.ENTREGADO);
        return toCargoLoadResponse(cargoLoadRepository.save(load));
    }

    @Transactional
    public CargoLoadResponse markPending(Long id) {
        CargoLoad load = cargoLoadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cargue no encontrado"));
        load.setStatus(CargoStatus.PENDIENTE);
        return toCargoLoadResponse(cargoLoadRepository.save(load));
    }

    @Transactional
    public void deleteLoad(Long id) {
        cargoLoadRepository.deleteById(id);
    }

    /* ── Settlements ── */
    @Transactional
    public CargoLoadResponse createOrUpdateSettlement(CargoSettlementRequest request) {
        CargoLoad load = cargoLoadRepository.findById(request.getCargoLoadId())
                .orElseThrow(() -> new RuntimeException("Cargue no encontrado"));

        CargoSettlement settlement = cargoSettlementRepository.findByCargoLoadId(load.getId())
                .orElse(CargoSettlement.builder().cargoLoad(load).build());

        settlement.setReturnedValue(request.getReturnedValue());
        settlement.setCoins(request.getCoins());
        settlement.setCash(request.getCash());
        settlement.setQr(request.getQr());
        settlement.setSecurity(request.getSecurity());
        settlement.setExpense(request.getExpense());
        settlement.calculateTotal();

        // Validación: Entregado + Devolución = Mercancía total
        double merchandise = zeroIfNull(load.getMerchandiseValue());
        double delivered = zeroIfNull(settlement.getDeliveredValue());
        double returned = zeroIfNull(settlement.getReturnedValue());
        if (Math.abs((delivered + returned) - merchandise) > 0.01) {
            throw new IllegalArgumentException(
                    String.format("Entregado ($%,.0f) + Devolución ($%,.0f) debe ser igual a la mercancía ($%,.0f)",
                            delivered, returned, merchandise));
        }

        cargoSettlementRepository.save(settlement);
        load.setSettlement(settlement);

        try {
            CargoReportResponse report = getReportByDate(load.getLoadDate());
            emailService.sendSettlementReport(report);
        } catch (Exception e) {
            // El envío de correo no debe fallar el cierre de jornada
            System.err.println("Error enviando reporte por correo: " + e.getMessage());
        }

        return toCargoLoadResponse(load);
    }

    @Transactional
    public List<CargoLoadResponse> createBulkSettlement(BulkSettlementRequest request) {
        LocalDate date = request.getDate() == null ? LocalDate.now() : request.getDate();
        List<CargoLoad> loads = cargoLoadRepository.findByLoadDateWithSettlement(date).stream()
                .filter(l -> l.getSettlement() == null)
                .filter(l -> l.getVehicle() != null && l.getVehicle().isActive())
                .collect(Collectors.toList());

        if (loads.isEmpty()) {
            throw new IllegalArgumentException("No hay carros pendientes por cerrar");
        }

        double totalMerchandise = loads.stream().mapToDouble(l -> zeroIfNull(l.getMerchandiseValue())).sum();
        double totalCash = zeroIfNull(request.getCash());
        double totalCoins = zeroIfNull(request.getCoins());
        double totalQr = zeroIfNull(request.getQr());
        double totalReturned = zeroIfNull(request.getReturnedValue());
        double totalSecurity = zeroIfNull(request.getSecurity());
        double totalExpense = zeroIfNull(request.getExpense());
        double totalDelivered = totalCash + totalCoins + totalQr;

        if (Math.abs((totalDelivered + totalReturned) - totalMerchandise) > 0.01) {
            throw new IllegalArgumentException(
                    String.format("Entregado ($%,.0f) + Devolución ($%,.0f) debe ser igual a la mercancía total ($%,.0f)",
                            totalDelivered, totalReturned, totalMerchandise));
        }

        List<CargoLoadResponse> responses = new ArrayList<>();
        double distributedCash = 0, distributedCoins = 0, distributedQr = 0;
        double distributedReturned = 0, distributedSecurity = 0, distributedExpense = 0;

        for (int i = 0; i < loads.size(); i++) {
            CargoLoad load = loads.get(i);
            boolean isLast = i == loads.size() - 1;
            double merchandise = zeroIfNull(load.getMerchandiseValue());
            double ratio = totalMerchandise > 0 ? merchandise / totalMerchandise : 0;

            double cash, coins, qr, returned, security, expense;
            if (isLast) {
                cash = totalCash - distributedCash;
                coins = totalCoins - distributedCoins;
                qr = totalQr - distributedQr;
                returned = totalReturned - distributedReturned;
                security = totalSecurity - distributedSecurity;
                expense = totalExpense - distributedExpense;
            } else {
                cash = Math.round(totalCash * ratio);
                coins = Math.round(totalCoins * ratio);
                qr = Math.round(totalQr * ratio);
                returned = Math.round(totalReturned * ratio);
                security = Math.round(totalSecurity * ratio);
                expense = Math.round(totalExpense * ratio);
                distributedCash += cash;
                distributedCoins += coins;
                distributedQr += qr;
                distributedReturned += returned;
                distributedSecurity += security;
                distributedExpense += expense;
            }

            CargoSettlement settlement = CargoSettlement.builder()
                    .cargoLoad(load)
                    .cash(cash)
                    .coins(coins)
                    .qr(qr)
                    .returnedValue(returned)
                    .security(security)
                    .expense(expense)
                    .build();
            settlement.calculateTotal();
            cargoSettlementRepository.save(settlement);
            load.setSettlement(settlement);
            load.setStatus(CargoStatus.ENTREGADO);
            cargoLoadRepository.save(load);

            responses.add(toCargoLoadResponse(load));
        }

        try {
            CargoReportResponse report = getReportByDate(date);
            emailService.sendSettlementReport(report);
        } catch (Exception e) {
            System.err.println("Error enviando reporte por correo: " + e.getMessage());
        }

        return responses;
    }

    /* ── Report ── */
    public CargoReportResponse getReportByDate(LocalDate date) {
        if (date == null) date = LocalDate.now();
        List<CargoLoad> loads = cargoLoadRepository.findByLoadDateWithSettlement(date);
        List<CargoLoadResponse> responses = loads.stream()
                .map(this::toCargoLoadResponse)
                .collect(Collectors.toList());

        double totalMerchandise = sum(loads, CargoLoad::getMerchandiseValue);
        double totalDelivered = sumSettlements(loads, CargoSettlement::getDeliveredValue);
        double totalReturned = sumSettlements(loads, CargoSettlement::getReturnedValue);
        double totalCoins = sumSettlements(loads, CargoSettlement::getCoins);
        double totalCash = sumSettlements(loads, CargoSettlement::getCash);
        double totalQr = sumSettlements(loads, CargoSettlement::getQr);
        double totalSecurity = sumSettlements(loads, CargoSettlement::getSecurity);
        double totalExpense = sumSettlements(loads, CargoSettlement::getExpense);
        double grandTotal = sumSettlements(loads, CargoSettlement::getTotal);

        long deliveredCount = loads.stream().filter(l -> l.getStatus() == CargoStatus.ENTREGADO).count();
        long pendingCount = loads.size() - deliveredCount;

        List<CompanyExpenseResponse> dayExpenses = companyExpenseService.findByFilters(date, date, null, null, null, null);
        double totalExpenses = dayExpenses.stream().mapToDouble(e -> e.getAmount() == null ? 0.0 : e.getAmount()).sum();
        Map<String, Double> expensesByCategory = dayExpenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.summingDouble(e -> e.getAmount() == null ? 0.0 : e.getAmount())
                ));

        return CargoReportResponse.builder()
                .date(date)
                .loads(responses)
                .totalMerchandise(totalMerchandise)
                .totalDelivered(totalDelivered)
                .totalReturned(totalReturned)
                .totalCoins(totalCoins)
                .totalCash(totalCash)
                .totalQr(totalQr)
                .totalSecurity(totalSecurity)
                .totalExpense(totalExpense)
                .grandTotal(grandTotal)
                .deliveredCount(deliveredCount)
                .pendingCount(pendingCount)
                .totalExpenses(totalExpenses)
                .expensesByCategory(expensesByCategory)
                .build();
    }

    /* ── Mappers ── */
    private DriverResponse toDriverResponse(Driver driver) {
        return DriverResponse.builder()
                .id(driver.getId())
                .name(driver.getName())
                .phone(driver.getPhone())
                .active(driver.isActive())
                .createdAt(driver.getCreatedAt())
                .build();
    }

    private VehicleResponse toVehicleResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .plate(vehicle.getPlate())
                .name(vehicle.getName())
                .driver(vehicle.getDriver() != null ? toDriverResponse(vehicle.getDriver()) : null)
                .active(vehicle.isActive())
                .createdAt(vehicle.getCreatedAt())
                .build();
    }

    private CargoLoadResponse toCargoLoadResponse(CargoLoad load) {
        return CargoLoadResponse.builder()
                .id(load.getId())
                .vehicle(toVehicleResponse(load.getVehicle()))
                .loadDate(load.getLoadDate())
                .merchandiseValue(load.getMerchandiseValue())
                .status(load.getStatus().name())
                .notes(load.getNotes())
                .createdAt(load.getCreatedAt())
                .settlement(load.getSettlement() != null ? toSettlementResponse(load.getSettlement()) : null)
                .build();
    }

    private CargoSettlementResponse toSettlementResponse(CargoSettlement settlement) {
        return CargoSettlementResponse.builder()
                .id(settlement.getId())
                .deliveredValue(settlement.getDeliveredValue())
                .returnedValue(settlement.getReturnedValue())
                .coins(settlement.getCoins())
                .cash(settlement.getCash())
                .qr(settlement.getQr())
                .security(settlement.getSecurity())
                .expense(settlement.getExpense())
                .total(settlement.getTotal())
                .settlementDate(settlement.getSettlementDate())
                .build();
    }

    private double zeroIfNull(Double value) {
        return value == null ? 0.0 : value;
    }

    private double sum(List<CargoLoad> loads, java.util.function.Function<CargoLoad, Double> extractor) {
        return loads.stream().mapToDouble(l -> extractor.apply(l) == null ? 0.0 : extractor.apply(l)).sum();
    }

    private double sumSettlements(List<CargoLoad> loads, java.util.function.Function<CargoSettlement, Double> extractor) {
        return loads.stream()
                .filter(l -> l.getSettlement() != null)
                .mapToDouble(l -> extractor.apply(l.getSettlement()) == null ? 0.0 : extractor.apply(l.getSettlement()))
                .sum();
    }
}
