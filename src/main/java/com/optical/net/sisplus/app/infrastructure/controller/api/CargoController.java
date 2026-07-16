package com.optical.net.sisplus.app.infrastructure.controller.api;

import com.optical.net.sisplus.app.infrastructure.service.CargoService;
import com.optical.net.sisplus.app.infrastructure.service.CompanyExpenseService;
import com.optical.net.sisplus.app.infrastructure.service.EmailService;
import com.optical.net.sisplus.app.infrastructure.web.cargo.*;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cargos")
public class CargoController {

    private final CargoService cargoService;
    private final CompanyExpenseService expenseService;
    private final EmailService emailService;

    public CargoController(CargoService cargoService, CompanyExpenseService expenseService, EmailService emailService) {
        this.cargoService = cargoService;
        this.expenseService = expenseService;
        this.emailService = emailService;
    }

    /* ── Drivers ── */
    @GetMapping("/drivers")
    public List<DriverResponse> getDrivers() {
        return cargoService.getAllDrivers();
    }

    @PostMapping("/drivers")
    public ResponseEntity<DriverResponse> createDriver(@RequestBody DriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cargoService.createDriver(request));
    }

    @PutMapping("/drivers/{id}")
    public DriverResponse updateDriver(@PathVariable Long id, @RequestBody DriverRequest request) {
        return cargoService.updateDriver(id, request);
    }

    @DeleteMapping("/drivers/{id}")
    public ResponseEntity<MessageResponse> deleteDriver(@PathVariable Long id) {
        return ResponseEntity.ok(new MessageResponse(cargoService.deleteDriver(id)));
    }

    /* ── Vehicles ── */
    @GetMapping("/vehicles")
    public List<VehicleResponse> getVehicles() {
        return cargoService.getAllVehicles();
    }

    @PostMapping("/vehicles")
    public ResponseEntity<VehicleResponse> createVehicle(@RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cargoService.createVehicle(request));
    }

    @PutMapping("/vehicles/{id}")
    public VehicleResponse updateVehicle(@PathVariable Long id, @RequestBody VehicleRequest request) {
        return cargoService.updateVehicle(id, request);
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<MessageResponse> deleteVehicle(@PathVariable Long id) {
        return ResponseEntity.ok(new MessageResponse(cargoService.deleteVehicle(id)));
    }

    /* ── Cargo Loads ── */
    @GetMapping
    public List<CargoLoadResponse> getLoads(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return cargoService.getLoadsByDate(date);
    }

    @PostMapping
    public ResponseEntity<CargoLoadResponse> createLoad(@RequestBody CargoLoadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cargoService.createLoad(request));
    }

    @PutMapping("/{id}")
    public CargoLoadResponse updateLoad(@PathVariable Long id, @RequestBody CargoLoadRequest request) {
        return cargoService.updateLoad(id, request);
    }

    @PostMapping("/{id}/deliver")
    public CargoLoadResponse markDelivered(@PathVariable Long id) {
        return cargoService.markDelivered(id);
    }

    @PostMapping("/{id}/pending")
    public CargoLoadResponse markPending(@PathVariable Long id) {
        return cargoService.markPending(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteLoad(@PathVariable Long id) {
        cargoService.deleteLoad(id);
        return ResponseEntity.ok("Cargue eliminado");
    }

    /* ── Settlements ── */
    @PostMapping("/settlements")
    public CargoLoadResponse createOrUpdateSettlement(@RequestBody CargoSettlementRequest request) {
        return cargoService.createOrUpdateSettlement(request);
    }

    @PostMapping("/settlements/bulk")
    public List<CargoLoadResponse> createBulkSettlement(@RequestBody BulkSettlementRequest request) {
        return cargoService.createBulkSettlement(request);
    }

    /* ── Report ── */
    @GetMapping("/report")
    public CargoReportResponse getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return cargoService.getReportByDate(date);
    }

    @PostMapping("/report/send")
    public ResponseEntity<MessageResponse> sendReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        CargoReportResponse report = cargoService.getReportByDate(date);
        emailService.sendSettlementReport(report);
        return ResponseEntity.ok(new MessageResponse("Reporte enviado al correo configurado"));
    }

    /* ── Company Expenses ── */
    @GetMapping("/expenses")
    public List<CompanyExpenseResponse> getExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Boolean hasAttachment
    ) {
        return expenseService.findByFilters(dateFrom, dateTo, categoryId, vehicleId, driverId, hasAttachment);
    }

    @GetMapping("/expenses/summary")
    public ExpenseSummaryResponse getExpenseSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return expenseService.getExpenseSummary(dateFrom, dateTo);
    }

    @PostMapping(value = "/expenses", consumes = "multipart/form-data")
    public ResponseEntity<CompanyExpenseResponse> createExpense(@ModelAttribute CompanyExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(request));
    }

    @PutMapping(value = "/expenses/{id}", consumes = "multipart/form-data")
    public CompanyExpenseResponse updateExpense(@PathVariable Long id, @ModelAttribute CompanyExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<String> deleteExpense(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.ok("Gasto eliminado");
    }

    @GetMapping("/expenses/{id}/attachment")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        return expenseService.downloadAttachment(id);
    }

    /* ── Expense Categories ── */
    @GetMapping("/expenses/categories")
    public List<ExpenseCategoryResponse> getCategories() {
        return expenseService.getAllCategories();
    }

    @PostMapping("/expenses/categories")
    public ResponseEntity<ExpenseCategoryResponse> createCategory(@RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.createCategory(request));
    }

    @PutMapping("/expenses/categories/{id}")
    public ExpenseCategoryResponse updateCategory(@PathVariable Long id, @RequestBody ExpenseCategoryRequest request) {
        return expenseService.updateCategory(id, request);
    }

    @DeleteMapping("/expenses/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable Long id) {
        expenseService.deleteCategory(id);
        return ResponseEntity.ok("Categoría eliminada");
    }
}
