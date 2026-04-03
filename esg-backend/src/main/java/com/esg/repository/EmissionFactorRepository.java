package com.esg.repository;

import com.esg.model.entity.EmissionFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, String> {
    List<EmissionFactor> findByRegion(String region);
    List<EmissionFactor> findByRegionAndCategory(String region, String category);
    Optional<EmissionFactor> findFirstByRegionAndCategoryOrderByValidFromDesc(String region, String category);
}
