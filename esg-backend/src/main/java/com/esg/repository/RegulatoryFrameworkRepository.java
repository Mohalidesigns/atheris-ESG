package com.esg.repository;

import com.esg.model.entity.RegulatoryFramework;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RegulatoryFrameworkRepository extends JpaRepository<RegulatoryFramework, String> {
    Optional<RegulatoryFramework> findByCode(String code);
    List<RegulatoryFramework> findByIsActiveTrue();
    List<RegulatoryFramework> findByJurisdiction(String jurisdiction);
}
