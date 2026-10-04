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
            + FILTERS + "order by lower(u.first_name), lower(u.last_name), u.id",
            countQuery = "select count(*) from auth.users u where u.tenant_id = :tenantId "
            + "and (:includePlatform or not auth.is_platform_user(u.id)) " + FILTERS,
            nativeQuery = true)
    Page<User> search(@Param("tenantId") UUID tenantId, @Param("q") String q,
                      @Param("includePlatform") boolean includePlatform, @Param("role") String role,
                      @Param("status") String status, Pageable pageable);

    /** Phase 2 user list filters: text, role (name; '' = any), status (active | inactive | '' = any). */
    String FILTERS = "and (:q = '' or lower(u.email) like :q or lower(u.first_name) like :q or lower(u.last_name) like :q "
            + "or lower(u.first_name || ' ' || u.last_name) like :q) "
            + "and (:role = '' or exists (select 1 from auth.user_roles ur join auth.roles r on r.id = ur.role_id "
            + "where ur.user_id = u.id and r.name = :role)) "
            + "and (:status = '' or (:status = 'active') = (u.is_active and u.deleted_at is null)) ";

    @Query(value = "select auth.is_platform_user(:id)", nativeQuery = true)
    boolean isPlatformUser(@Param("id") UUID id);
}
