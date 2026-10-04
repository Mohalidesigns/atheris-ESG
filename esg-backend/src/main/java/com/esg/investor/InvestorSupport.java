package com.esg.investor;

import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What the investor services share. A "manager" (investor:share) prepares and sees everything; anyone else with
 * investor:read — the INVESTOR_VIEWER — sees only what is published, shared with them, or marked visible (D35 (1)).
 */
@Component
public class InvestorSupport {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InvestorSupport(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    boolean manager() {
        return CurrentUser.hasPermission("investor:share");
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    int year(Integer year) {
        int y = year == null ? today().getYear() : year;
        if (y < 2000 || y > today().getYear() + 1) {
            throw FieldValidationException.of("year", "must be between 2000 and next year");
        }
        return y;
    }

    /** Active users in the tenant who hold investor:read but not investor:share (the people a data room is for). */
    List<InvestorDtos.Person> investors() {
        return jdbc.query("select u.id, u.first_name || ' ' || u.last_name as name, u.email from auth.users u where u.is_active and u.deleted_at is null "
                + "and not auth.is_platform_user(u.id) and exists (" + holds("read") + ") and not exists (" + holds("share") + ") order by name",
                (rs, i) -> new InvestorDtos.Person(rs.getObject(1, java.util.UUID.class), rs.getString(2), rs.getString(3)));
    }

    private static String holds(String action) {
        return "select 1 from auth.user_roles ur join auth.role_permissions rp on rp.role_id = ur.role_id join auth.permissions p on p.id = rp.permission_id "
                + "where ur.user_id = u.id and (ur.expires_at is null or ur.expires_at > now()) and p.resource = 'investor' and p.action = '" + action + "'";
    }
}
