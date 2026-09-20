package com.kabadiwala.backend.material;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WasteCategoryRepository extends JpaRepository<WasteCategory, String> {
    List<WasteCategory> findByActiveTrueOrderByDisplayNameAsc();
}
