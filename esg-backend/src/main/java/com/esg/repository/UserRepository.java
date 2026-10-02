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
    /** Active users counting towards the subscription's seats: platform-scope users (SUPER_ADMIN) don't (S5). */
    @Query(value = "select count(*) from auth.users u where u.tenant_id = :tenantId and u.is_active "
            + "and u.deleted_at is null and not auth.is_platform_user(u.id)", nativeQuery = true)
    long countSeats(@Param("tenantId") UUID tenantId);

    /**
     * Case-insensitive search on email/first/last name; pass "" for no filter, otherwise "%term%" in lower case.
     * Platform-scope users are left out unless {@code includePlatform} (the caller is one) (S5).
     */
    @Query(value = "select * from auth.users u where u.tenant_id = :tenantId and (:includePlatform or not auth.is_platform_user(u.id)) "
            + "and (:q = '' or lower(u.email) like :q or lower(u.first_name) like :q or lower(u.last_name) like :q) "
            + "order by lower(u.last_name), lower(u.first_name), u.id",
            countQuery = "select count(*) from auth.users u where u.tenant_id = :tenantId "
            + "and (:includePlatform or not auth.is_platform_user(u.id)) "
            + "and (:q = '' or lower(u.email) like :q or lower(u.first_name) like :q or lower(u.last_name) like :q)",
            nativeQuery = true)
    Page<User> search(@Param("tenantId") UUID tenantId, @Param("q") String q,
                      @Param("includePlatform") boolean includePlatform, Pageable pageable);

    @Query(value = "select auth.is_platform_user(:id)", nativeQuery = true)
    boolean isPlatformUser(@Param("id") UUID id);
}
