package com.esg.repository;

import com.esg.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByTenantId(UUID tenantId);
    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    /** Case-insensitive search on email/first/last name; pass "" for no filter, otherwise "%term%" in lower case. */
    @Query("select u from User u where u.tenantId = :tenantId and (:q = '' or lower(u.email) like :q "
            + "or lower(u.firstName) like :q or lower(u.lastName) like :q)")
    Page<User> search(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);
}
