package com.optical.net.sisplus.app.infrastructure.service;

import com.optical.net.sisplus.app.infrastructure.entity.*;
import com.optical.net.sisplus.app.infrastructure.repository.*;
import com.optical.net.sisplus.app.infrastructure.web.cargo.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompanyExpenseService {

    private final CompanyExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final CargoSettlementRepository settlementRepository;

    @Value("${app.uploads.expenses-dir:uploads/expenses}")
    private String expensesDir;

    private Path uploadPath;

    public CompanyExpenseService(CompanyExpenseRepository expenseRepository,
                                 ExpenseCategoryRepository categoryRepository,
                                 VehicleRepository vehicleRepository,
                                 DriverRepository driverRepository,
                                 CargoSettlementRepository settlementRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.settlementRepository = settlementRepository;
    }

    @PostConstruct
    public void init() throws IOException {
        this.uploadPath = Paths.get(expensesDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
    }

    /* ── Expense Queries ── */
    public List<CompanyExpenseResponse> findByFilters(LocalDate dateFrom,
                                                       LocalDate dateTo,
                                                       Long categoryId,
                                                       Long vehicleId,
                                                       Long driverId,
                                                       Boolean hasAttachment) {
        Specification<CompanyExpense> spec = (root, query, cb) -> null;
        if (dateFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), dateFrom));
        }
        if (dateTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), dateTo));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("category", JoinType.LEFT).get("id"), categoryId));
        }
        if (vehicleId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("vehicle", JoinType.LEFT).get("id"), vehicleId));
        }
        if (driverId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("driver", JoinType.LEFT).get("id"), driverId));
        }
        if (hasAttachment != null) {
            spec = spec.and((root, query, cb) -> hasAttachment
                    ? cb.isNotNull(root.get("attachmentPath"))
                    : cb.isNull(root.get("attachmentPath")));
        }

        return expenseRepository.findAll(spec, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("expenseDate"),
                org.springframework.data.domain.Sort.Order.desc("createdAt")
        )).stream().map(this::toResponse).collect(Collectors.toList());
    }

    public CompanyExpenseResponse findExpenseById(Long id) {
        return toResponse(expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado")));
    }

    public ExpenseSummaryResponse getExpenseSummary(LocalDate dateFrom, LocalDate dateTo) {
        List<CompanyExpenseResponse> expenses = findByFilters(dateFrom, dateTo, null, null, null, null);
        double total = expenses.stream().mapToDouble(e -> e.getAmount() == null ? 0.0 : e.getAmount()).sum();
        Map<String, Double> byCategory = expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.summingDouble(e -> e.getAmount() == null ? 0.0 : e.getAmount())
                ));
        return ExpenseSummaryResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .total(total)
                .expensesByCategory(byCategory)
                .count((long) expenses.size())
                .build();
    }

    /* ── Expense CRUD ── */
    @Transactional
    public CompanyExpenseResponse create(CompanyExpenseRequest request) {
        log.debug("Creating expense: date={}, categoryId={}, amount={}, vehicleId={}, driverId={}",
                request.getExpenseDate(), request.getCategoryId(), request.getAmount(),
                request.getVehicleId(), request.getDriverId());
        CompanyExpense expense = buildFromRequest(new CompanyExpense(), request);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public CompanyExpenseResponse update(Long id, CompanyExpenseRequest request) {
        log.debug("Updating expense id={}: categoryId={}, amount={}", id, request.getCategoryId(), request.getAmount());
        CompanyExpense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        if (Boolean.TRUE.equals(request.getRemoveAttachment())) {
            deleteAttachmentFile(expense.getAttachmentPath());
            expense.setAttachmentPath(null);
            expense.setAttachmentOriginalName(null);
            expense.setAttachmentContentType(null);
        }

        return toResponse(expenseRepository.save(buildFromRequest(expense, request)));
    }

    @Transactional
    public void delete(Long id) {
        CompanyExpense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        deleteAttachmentFile(expense.getAttachmentPath());
        expenseRepository.delete(expense);
    }

    /* ── Attachment download ── */
    public ResponseEntity<Resource> downloadAttachment(Long id) {
        CompanyExpense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        if (!expense.hasAttachment()) {
            throw new RuntimeException("Este gasto no tiene soporte adjunto");
        }

        Path filePath = uploadPath.resolve(expense.getAttachmentPath()).normalize();
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Archivo adjunto no encontrado en disco");
        }

        String contentType = StringUtils.hasText(expense.getAttachmentContentType())
                ? expense.getAttachmentContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + expense.getAttachmentOriginalName() + "\"")
                .body(resource);
    }

    /* ── Category CRUD ── */
    public List<ExpenseCategoryResponse> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    public ExpenseCategoryResponse getCategoryById(Long id) {
        return toCategoryResponse(categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada")));
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(ExpenseCategoryRequest request) {
        validateCategoryName(request.getName(), null);
        ExpenseCategoryEntity category = new ExpenseCategoryEntity();
        category.setName(request.getName().trim());
        category.setColor(request.getColor());
        if (request.getActive() != null) category.setActive(request.getActive());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public ExpenseCategoryResponse updateCategory(Long id, ExpenseCategoryRequest request) {
        validateCategoryName(request.getName(), id);
        ExpenseCategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        category.setName(request.getName().trim());
        category.setColor(request.getColor());
        if (request.getActive() != null) category.setActive(request.getActive());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        ExpenseCategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        long expenseCount = expenseRepository.countByCategoryId(id);
        if (expenseCount > 0) {
            ExpenseCategoryEntity fallback = categoryRepository.findAll().stream()
                    .filter(c -> c.isActive() && !c.getId().equals(id))
                    .findFirst()
                    .orElseGet(() -> {
                        ExpenseCategoryEntity cat = ExpenseCategoryEntity.builder()
                                .name("Otros")
                                .color("#64748B")
                                .active(true)
                                .build();
                        return categoryRepository.save(cat);
                    });

            expenseRepository.reassignCategory(category, fallback);
        }

        categoryRepository.delete(category);
    }

    private void validateCategoryName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new RuntimeException("El nombre de la categoría es obligatorio");
        }
        categoryRepository.findByNameIgnoreCase(name.trim())
                .ifPresent(existing -> {
                    if (excludeId == null || !existing.getId().equals(excludeId)) {
                        throw new RuntimeException("Ya existe una categoría con ese nombre");
                    }
                });
    }

    /* ── Helpers ── */
    private CompanyExpense buildFromRequest(CompanyExpense expense, CompanyExpenseRequest request) {
        expense.setExpenseDate(request.getExpenseDate());
        expense.setCategory(resolveCategory(request.getCategoryId()));
        expense.setAmount(request.getAmount());
        expense.setDescription(request.getDescription());

        expense.setVehicle(resolveVehicle(request.getVehicleId()));
        expense.setDriver(resolveDriver(request.getDriverId()));
        expense.setSettlement(resolveSettlement(request.getSettlementId()));

        if (request.getAttachment() != null && !request.getAttachment().isEmpty()) {
            deleteAttachmentFile(expense.getAttachmentPath());
            saveAttachment(expense, request.getAttachment());
        }

        return expense;
    }

    private ExpenseCategoryEntity resolveCategory(Long id) {
        if (id == null) throw new RuntimeException("La categoría es obligatoria");
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
    }

    private Vehicle resolveVehicle(Long id) {
        return id != null ? vehicleRepository.findById(id).orElse(null) : null;
    }

    private Driver resolveDriver(Long id) {
        return id != null ? driverRepository.findById(id).orElse(null) : null;
    }

    private CargoSettlement resolveSettlement(Long id) {
        return id != null ? settlementRepository.findById(id).orElse(null) : null;
    }

    private void saveAttachment(CompanyExpense expense, MultipartFile file) {
        String original = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > 0) ext = original.substring(dot);

        String storedName = UUID.randomUUID() + ext;
        Path target = uploadPath.resolve(storedName).normalize();

        if (!target.startsWith(uploadPath)) {
            throw new RuntimeException("Ruta de archivo inválida");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo adjunto", e);
        }

        expense.setAttachmentPath(storedName);
        expense.setAttachmentOriginalName(original);
        expense.setAttachmentContentType(file.getContentType());
    }

    private void deleteAttachmentFile(String fileName) {
        if (!StringUtils.hasText(fileName)) return;
        try {
            Path target = uploadPath.resolve(fileName).normalize();
            if (target.startsWith(uploadPath)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
        }
    }

    private CompanyExpenseResponse toResponse(CompanyExpense expense) {
        return CompanyExpenseResponse.builder()
                .id(expense.getId())
                .expenseDate(expense.getExpenseDate())
                .category(toCategoryResponse(expense.getCategory()))
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .vehicle(expense.getVehicle() != null ? toVehicleResponse(expense.getVehicle()) : null)
                .driver(expense.getDriver() != null ? toDriverResponse(expense.getDriver()) : null)
                .settlement(expense.getSettlement() != null ? toSettlementResponse(expense.getSettlement()) : null)
                .hasAttachment(expense.hasAttachment())
                .attachmentOriginalName(expense.getAttachmentOriginalName())
                .attachmentContentType(expense.getAttachmentContentType())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    private ExpenseCategoryResponse toCategoryResponse(ExpenseCategoryEntity category) {
        if (category == null) return null;
        return ExpenseCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .color(category.getColor())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

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

    private CargoSettlementResponse toSettlementResponse(CargoSettlement settlement) {
        return CargoSettlementResponse.builder()
                .id(settlement.getId())
                .deliveredValue(settlement.getDeliveredValue())
                .returnedValue(settlement.getReturnedValue())
                .coins(settlement.getCoins())
                .cash(settlement.getCash())
                .qr(settlement.getQr())
                .security(settlement.getSecurity())
                .total(settlement.getTotal())
                .settlementDate(settlement.getSettlementDate())
                .build();
    }
}
