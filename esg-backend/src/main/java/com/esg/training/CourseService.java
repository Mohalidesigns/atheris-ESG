package com.esg.training;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.security.CurrentUser;
import com.esg.training.TrainingDtos.ActionRequest;
import com.esg.training.TrainingDtos.Course;
import com.esg.training.TrainingDtos.CourseRequest;
import com.esg.training.TrainingDtos.Module;
import com.esg.training.TrainingDtos.ModuleRequest;
import com.esg.training.TrainingDtos.Path;
import com.esg.training.TrainingDtos.PathRequest;
import com.esg.training.TrainingDtos.Question;
import com.esg.training.TrainingDtos.QuestionRequest;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Courses and learning paths (WP-42, D37 (3), (6); TRD §8.12 LearningManagementEngine). A course is written as a draft
 * with its modules and quiz, then published (fixed: copy to revise) or retired. Learners see published courses only.
 */
@Service
public class CourseService {

    // no-mock-gate: config — course lifecycle (V034 courses status check)
    static final Map<String, Set<String>> ACTIONS = Map.of("draft", Set.of("publish"), "published", Set.of("retire", "copy"), "retired", Set.of("copy"));

    private final JdbcTemplate jdbc;

    public CourseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static boolean manager() {
        return CurrentUser.hasPermission("training:create");
    }

    // ---------------------------------------------------------------- courses

    public List<Course> list(String status) {
        if (status != null && !ACTIONS.containsKey(status)) {
            throw FieldValidationException.of("status", "must be draft, published or retired");
        }
        String where = "where c.deleted_at is null" + (manager() ? "" : " and c.status = 'published'") + (status == null ? "" : " and c.status = ?");
        return jdbc.query(select() + where + " order by c.status, c.title", (rs, i) -> course(rs, false), status == null ? new Object[0] : new Object[] {status});
    }

    public Course get(UUID id) {
        Course c = jdbc.query(select() + "where c.id = ? and c.deleted_at is null", (rs, i) -> course(rs, true), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Course"));
        if (!manager() && !"published".equals(c.status()) && !enrolled(id)) {
            throw ApiException.notFound("Course");
        }
        return c;
    }

    private boolean enrolled(UUID courseId) {
        UUID me = CurrentUser.userIdOrNull();
        return me != null && Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from training.enrollments where course_id = ? and user_id = ?)",
                Boolean.class, courseId, me));
    }

    @Transactional
    public Course create(CourseRequest r) {
        check(r, null);
        UUID id = UUID.randomUUID();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into training.courses (id, code, title, description, category, delivery, duration_hours, passing_score, "
                    + "certificate_validity_months, prerequisite_ids) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setString(2, r.code().trim());
            ps.setString(3, r.title().trim());
            ps.setString(4, blank(r.description()));
            ps.setString(5, r.category());
            ps.setString(6, r.delivery());
            ps.setBigDecimal(7, r.durationHours());
            ps.setInt(8, r.passingScore());
            ps.setObject(9, r.certificateValidityMonths());
            ps.setArray(10, con.createArrayOf("uuid", r.prerequisiteIds().stream().distinct().toArray()));
            return ps;
        });
        content(id, r);
        return get(id);
    }

    @Transactional
    public Course update(UUID id, CourseRequest r) {
        Course c = get(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        if (!"draft".equals(c.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This course is " + c.status() + " and fixed; copy it to revise it.");
        }
        check(r, id);
        int n = jdbc.update(con -> {
            var ps = con.prepareStatement("update training.courses set code = ?, title = ?, description = ?, category = ?, delivery = ?, duration_hours = ?, "
                    + "passing_score = ?, certificate_validity_months = ?, prerequisite_ids = ?, version = version + 1 where id = ? and version = ? "
                    + "and deleted_at is null");
            ps.setString(1, r.code().trim());
            ps.setString(2, r.title().trim());
            ps.setString(3, blank(r.description()));
            ps.setString(4, r.category());
            ps.setString(5, r.delivery());
            ps.setBigDecimal(6, r.durationHours());
            ps.setInt(7, r.passingScore());
            ps.setObject(8, r.certificateValidityMonths());
            ps.setArray(9, con.createArrayOf("uuid", r.prerequisiteIds().stream().distinct().toArray()));
            ps.setObject(10, id);
            ps.setLong(11, r.version());
            return ps;
        });
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This course was changed by someone else. Reload and try again.");
        }
        jdbc.update("delete from training.course_modules where course_id = ?", id);
        jdbc.update("delete from training.quiz_questions where course_id = ?", id);
        content(id, r);
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        Course c = get(id);
        if (c.enrolled() > 0) {
            throw ApiException.conflict("ESG-409-IN-USE", "People are enrolled in this course; retire it instead.");
        }
        jdbc.update("update training.courses set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    @Transactional
    public Course act(UUID id, ActionRequest r) {
        Course c = get(id);
        if (!c.actions().contains(r.action())) {
            throw ApiException.conflict("ESG-409-STATE", "You can't " + r.action() + " a course that is " + c.status() + ".");
        }
        switch (r.action()) {
            case "publish" -> {
                if (c.modules().isEmpty()) {
                    throw FieldValidationException.of("modules", "add at least one module before publishing");
                }
                update("update training.courses set status = 'published', published_at = now(), version = version + 1 where id = ? and version = ?", id,
                        r.version());
            }
            case "retire" -> update("update training.courses set status = 'retired', version = version + 1 where id = ? and version = ?", id, r.version());
            default -> {
                String code = c.code().length() > 26 ? c.code().substring(0, 26) : c.code();
                String copyCode = code + "-V" + (1 + jdbc.queryForObject("select count(*) from training.courses where code like ?", Integer.class, code + "-V%"));
                UUID copy = UUID.randomUUID();
                jdbc.update("insert into training.courses (id, code, title, description, category, delivery, duration_hours, passing_score, "
                        + "certificate_validity_months, prerequisite_ids) select ?, ?, title, description, category, delivery, duration_hours, passing_score, "
                        + "certificate_validity_months, prerequisite_ids from training.courses where id = ?", copy, copyCode, id);
                jdbc.update("insert into training.course_modules (course_id, sort_order, title, kind, body, file_id, url, minutes) select ?, sort_order, title, "
                        + "kind, body, file_id, url, minutes from training.course_modules where course_id = ?", copy, id);
                jdbc.update("insert into training.quiz_questions (course_id, sort_order, question, options, correct_index) select ?, sort_order, question, "
                        + "options, correct_index from training.quiz_questions where course_id = ?", copy, id);
                return get(copy);
            }
        }
        return get(id);
    }

    private void update(String sql, UUID id, Long version) {
        if (jdbc.update(sql, id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This course was changed by someone else. Reload and try again.");
        }
    }

    private void check(CourseRequest r, UUID id) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from training.courses where code = ? and deleted_at is null and id is distinct from ?)",
                Boolean.class, r.code().trim(), id))) {
            errors.put("code", "is already used by another course");
        }
        for (UUID p : r.prerequisiteIds()) {
            if (p.equals(id) || !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from training.courses where id = ? and deleted_at is null)",
                    Boolean.class, p))) {
                errors.put("prerequisiteIds", "prerequisites must be other courses");
            }
        }
        for (int i = 0; i < r.modules().size(); i++) {
            ModuleRequest m = r.modules().get(i);
            String need = switch (m.kind()) {
                case "text" -> blank(m.body()) == null ? "body" : null;
                case "pdf" -> m.fileId() == null ? "fileId" : null;
                default -> blank(m.url()) == null ? "url" : null;
            };
            if (need != null) {
                errors.put("modules[" + i + "]." + need, "pdf".equals(m.kind()) ? "attach the PDF" : "video".equals(m.kind()) ? "give the video link" : "write the text");
            }
        }
        for (int i = 0; i < r.questions().size(); i++) {
            QuestionRequest q = r.questions().get(i);
            if (q.correctIndex() >= q.options().size()) {
                errors.put("questions[" + i + "].correctIndex", "must be one of the options");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void content(UUID id, CourseRequest r) {
        for (int i = 0; i < r.modules().size(); i++) {
            ModuleRequest m = r.modules().get(i);
            jdbc.update("insert into training.course_modules (course_id, sort_order, title, kind, body, file_id, url, minutes) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, i + 1, m.title().trim(), m.kind(), "text".equals(m.kind()) ? m.body() : null, "pdf".equals(m.kind()) ? m.fileId() : null,
                    "video".equals(m.kind()) ? m.url().trim() : null, m.minutes());
            if ("pdf".equals(m.kind())) {
                jdbc.update("update files.file_metadata set resource_type = 'training_course', resource_id = ? where id = ? and resource_id is null", id, m.fileId());
            }
        }
        for (int i = 0; i < r.questions().size(); i++) {
            QuestionRequest q = r.questions().get(i);
            int order = i + 1;
            jdbc.update(con -> {
                var ps = con.prepareStatement("insert into training.quiz_questions (course_id, sort_order, question, options, correct_index) values (?, ?, ?, ?, ?)");
                ps.setObject(1, id);
                ps.setInt(2, order);
                ps.setString(3, q.question().trim());
                ps.setArray(4, con.createArrayOf("varchar", q.options().stream().map(String::trim).toArray()));
                ps.setInt(5, q.correctIndex());
                return ps;
            });
        }
    }

    private static String select() {
        return "select c.*, (select count(*) from training.enrollments e where e.course_id = c.id and e.status <> 'withdrawn') as enrolled, "
                + "(select count(*) from training.enrollments e where e.course_id = c.id and e.status = 'passed') as passed from training.courses c ";
    }

    private Course course(ResultSet rs, boolean full) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        List<UUID> prereqs = uuids(rs.getArray("prerequisite_ids"));
        List<String> prereqTitles = prereqs.isEmpty() ? List.of() : jdbc.queryForList("select title from training.courses where id = any(?::uuid[]) order by title",
                String.class, (Object) prereqs.stream().map(UUID::toString).toArray(String[]::new));
        List<Module> modules = jdbc.query("select m.*, f.original_name from training.course_modules m left join files.file_metadata f on f.id = m.file_id "
                + "where m.course_id = ? order by m.sort_order", (r, i) -> new Module(r.getObject("id", UUID.class), r.getString("title"), r.getString("kind"),
                full ? r.getString("body") : null, r.getObject("file_id", UUID.class), r.getString("original_name"), r.getString("url"), r.getInt("minutes")), id);
        boolean manager = manager();
        List<Question> questions = jdbc.query("select * from training.quiz_questions where course_id = ? order by sort_order", (r, i) -> new Question(
                r.getObject("id", UUID.class), r.getString("question"), Arrays.stream((Object[]) r.getArray("options").getArray()).map(String::valueOf).toList(),
                manager ? (Integer) r.getInt("correct_index") : null), id);
        List<String> actions = manager ? new ArrayList<>(ACTIONS.get(status)) : List.of();
        Timestamp published = rs.getTimestamp("published_at");
        return new Course(id, rs.getString("code"), rs.getString("title"), rs.getString("description"), rs.getString("category"), rs.getString("delivery"),
                rs.getBigDecimal("duration_hours"), rs.getInt("passing_score"), (Integer) rs.getObject("certificate_validity_months"), prereqs, prereqTitles, status,
                published == null ? null : published.toInstant(), modules, questions, rs.getInt("enrolled"), rs.getInt("passed"), actions, rs.getLong("version"));
    }

    // ---------------------------------------------------------------- learning paths

    public List<Path> paths() {
        return jdbc.query("select * from training.learning_paths where deleted_at is null" + (manager() ? "" : " and status = 'active'")
                + " order by status, lower(name)", (rs, i) -> path(rs));
    }

    public Path path(UUID id) {
        return jdbc.query("select * from training.learning_paths where id = ? and deleted_at is null", (rs, i) -> path(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Learning path"));
    }

    @Transactional
    public Path createPath(PathRequest r) {
        checkPath(r, null);
        UUID id = UUID.randomUUID();
        savePath("insert into training.learning_paths (name, description, target_role, course_ids, status, id) values (?, ?, ?, ?, ?, ?)", r, id, null);
        return path(id);
    }

    @Transactional
    public Path updatePath(UUID id, PathRequest r) {
        path(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        checkPath(r, id);
        if (savePath("update training.learning_paths set name = ?, description = ?, target_role = ?, course_ids = ?, status = ?, version = version + 1 "
                + "where id = ? and version = ? and deleted_at is null", r, id, r.version()) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This path was changed by someone else. Reload and try again.");
        }
        return path(id);
    }

    @Transactional
    public void deletePath(UUID id) {
        path(id);
        jdbc.update("update training.learning_paths set deleted_at = now(), version = version + 1 where id = ?", id);
    }

    private int savePath(String sql, PathRequest r, UUID id, Long version) {
        return jdbc.update(con -> {
            var ps = con.prepareStatement(sql);
            ps.setString(1, r.name().trim());
            ps.setString(2, blank(r.description()));
            ps.setString(3, blank(r.targetRole()));
            ps.setArray(4, con.createArrayOf("uuid", r.courseIds().stream().distinct().toArray()));
            ps.setString(5, r.status());
            ps.setObject(6, id);
            if (version != null) {
                ps.setLong(7, version);
            }
            return ps;
        });
    }

    private void checkPath(PathRequest r, UUID id) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from training.learning_paths where lower(name) = lower(?) and deleted_at is null "
                + "and id is distinct from ?)", Boolean.class, r.name().trim(), id))) {
            errors.put("name", "is already used by another path");
        }
        if (blank(r.targetRole()) != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from auth.system_roles where name = ? "
                + "and name not in ('SUPER_ADMIN', 'INVESTOR_VIEWER'))", Boolean.class, r.targetRole()))) {
            errors.put("targetRole", "must be one of the organisation's roles");
        }
        Integer found = jdbc.queryForObject("select count(*) from training.courses where id = any(?::uuid[]) and deleted_at is null and status <> 'draft'",
                Integer.class, (Object) r.courseIds().stream().distinct().map(UUID::toString).toArray(String[]::new));
        if (found == null || found != r.courseIds().stream().distinct().count()) {
            errors.put("courseIds", "must be published (or retired) courses");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private Path path(ResultSet rs) throws SQLException {
        List<UUID> ids = uuids(rs.getArray("course_ids"));
        Map<UUID, String> titles = new LinkedHashMap<>();
        jdbc.query("select id, title from training.courses where id = any(?::uuid[])", r -> {
            titles.put(r.getObject(1, UUID.class), r.getString(2));
        }, (Object) ids.stream().map(UUID::toString).toArray(String[]::new));
        return new Path(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"), rs.getString("target_role"), ids,
                ids.stream().map(i -> titles.getOrDefault(i, "(removed course)")).toList(), rs.getString("status"), rs.getLong("version"));
    }

    static List<UUID> uuids(Array a) throws SQLException {
        return a == null ? List.of() : Arrays.stream((Object[]) a.getArray()).map(o -> o instanceof UUID u ? u : UUID.fromString(String.valueOf(o))).toList();
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
