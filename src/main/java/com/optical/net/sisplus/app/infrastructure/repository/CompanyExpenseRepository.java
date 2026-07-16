package com.optical.net.sisplus.app.infrastructure.repository;

import com.optical.net.sisplus.app.infrastructure.entity.CompanyExpense;
import com.optical.net.sisplus.app.infrastructure.entity.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyExpenseRepository extends JpaRepository<CompanyExpense, Long>, JpaSpecificationExecutor<CompanyExpense> {

    long countByCategoryId(Long categoryId);

    @Modifying
    @Query("UPDATE CompanyExpense e SET e.category = :newCategory WHERE e.category = :oldCategory")
    void reassignCategory(@Param("oldCategory") ExpenseCategoryEntity oldCategory,
                          @Param("newCategory") ExpenseCategoryEntity newCategory);
}
