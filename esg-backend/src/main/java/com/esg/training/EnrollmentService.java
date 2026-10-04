package com.esg.training;

import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.training.TrainingDtos.AssignPathRequest;
import com.esg.training.TrainingDtos.AssignRequest;
import com.esg.training.TrainingDtos.AssignResult;
import com.esg.training.TrainingDtos.AttendanceRequest;
import com.esg.training.TrainingDtos.Certificate;
import com.esg.training.TrainingDtos.Enrollment;
import com.esg.training.TrainingDtos.Gap;
import com.esg.training.TrainingDtos.Path;
import com.esg.training.TrainingDtos.Person;
import com.esg.training.TrainingDtos.Progress;
import com.esg.training.TrainingDtos.QuizRequest;
import com.esg.training.TrainingDtos.TeamRow;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrolments, progress, quizzes, classroom attendance and certificates (WP-42, D37 (4)–(7); TRD §8.12 AssessmentEngine,
 * CertificateGenerator, CompetencyTracker; TRD POST /training/enrollments, GET /training/progress).
 */
@Service
public class EnrollmentService {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final String SELECT = "select e.*, c.code, c.title, c.delivery, c.duration_hours, c.passing_score, c.certificate_validity_months, "
            + "u.first_name || ' ' || u.last_name as user_name, (select count(*) from training.course_modules m where m.course_id = c.id) as modules_total "
            + "from training.enrollments e join training.courses c on c.id = e.course_id join auth.users u on u.id = e.user_id ";
    private static final String PEOPLE = "select u.id, u.first_name || ' ' || u.last_name as name, u.email, coalesce(array_agg(distinct r.name) "
            + "filter (where r.name is not null), '{}') as roles from auth.users u left join auth.user_roles ur on ur.user_id = u.id "
            + "and (ur.expires_at is null or ur.expires_at > now()) left join auth.roles r on r.id = ur.role_id where u.is_active and u.deleted_at is null "
            + "and not auth.is_platform_user(u.id) group by u.id, u.first_name, u.last_name, u.email";

    private final JdbcTemplate jdbc;
    private final CourseService courses;
    private final NotificationService notifications;
    private final Clock clock;
    private final String publicUrl;

    public EnrollmentService(JdbcTemplate jdbc, CourseService courses, NotificationService notifications, Clock clock,
                             @Value("${esg.app.public-url:http://localhost:5173}") String publicUrl) {
        this.jdbc = jdbc;
        this.courses = courses;
        this.notifications = notifications;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LAGOS));
    }

    String verifyUrl(String code) {
        return publicUrl + "/verify-certificate/" + code;
    }

    // ---------------------------------------------------------------- people

    public List<Person> people() {
        return jdbc.query(PEOPLE + " order by name", (rs, i) -> person(rs));
    }

    private static Person person(ResultSet rs) throws SQLException {
        Object[] roles = (Object[]) rs.getArray("roles").getArray();
        return new Person(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                java.util.Arrays.stream(roles).map(String::valueOf).filter(r -> !"INVESTOR_VIEWER".equals(r)).sorted().toList());
    }

    // ---------------------------------------------------------------- enrolling

    /** TRD POST /training/enrollments for oneself: a published course whose prerequisites are passed. */
    @Transactional
    public Enrollment enrolSelf(UUID courseId) {
        UUID me = CurrentUser.access().userId();
        var c = courses.get(courseId);
        if (!"published".equals(c.status())) {
            throw ApiException.conflict("ESG-409-STATE", "Only published courses can be joined.");
        }
        requirePrerequisites(courseId, me);
        UUID id = enrol(courseId, me, "self", null, null);
        if (id == null) {
            throw ApiException.conflict("ESG-409-DUPLICATE", "You are already enrolled in this course.");
        }
        return get(id);
    }

    @Transactional
    public AssignResult assign(AssignRequest r) {
        var c = courses.get(r.courseId());
        if (!"published".equals(c.status())) {
            throw FieldValidationException.of("courseId", "only published courses can be assigned");
        }
        if (r.dueOn() != null && r.dueOn().isBefore(today())) {
            throw FieldValidationException.of("dueOn", "can't be in the past");
        }
        List<UUID> known = people().stream().map(Person::id).toList();
        int enrolled = 0;
        int already = 0;
        for (UUID u : r.userIds().stream().distinct().toList()) {
            if (!known.contains(u)) {
                throw FieldValidationException.of("userIds", "must be people in your organisation");
            }
            UUID id = enrol(r.courseId(), u, "assigned", CurrentUser.access().userId(), r.dueOn());
            if (id == null) {
                already++;
            } else {
                enrolled++;
                notifyAssigned(u, c.title(), c.durationHours(), r.dueOn());
            }
        }
        return new AssignResult(enrolled, already);
    }

    /** Assigns every course of a path to the given people, or to everyone holding the path's role. */
    @Transactional
    public AssignResult assignPath(AssignPathRequest r) {
        Path p = courses.path(r.pathId());
        List<UUID> users = r.userIds() != null && !r.userIds().isEmpty() ? r.userIds()
                : people().stream().filter(x -> p.targetRole() != null && x.roles().contains(p.targetRole())).map(Person::id).toList();
        if (users.isEmpty()) {
            throw FieldValidationException.of("userIds", "choose people, or give the path a role that someone holds");
        }
        int enrolled = 0;
        int already = 0;
        for (UUID courseId : p.courseIds()) {
            var c = courses.get(courseId);
            if (!"published".equals(c.status())) {
                continue;
            }
            for (UUID u : users) {
                UUID id = enrol(courseId, u, "path", CurrentUser.access().userId(), r.dueOn());
                if (id == null) {
                    already++;
                } else {
                    enrolled++;
                    notifyAssigned(u, c.title(), c.durationHours(), r.dueOn());
                }
            }
        }
        return new AssignResult(enrolled, already);
    }

    /** null when the person is already enrolled (a withdrawn enrolment is re-opened). */
    private UUID enrol(UUID courseId, UUID userId, String source, UUID by, LocalDate due) {
        List<Map<String, Object>> existing = jdbc.queryForList("select id, status from training.enrollments where course_id = ? and user_id = ?", courseId, userId);
        if (!existing.isEmpty()) {
            if (!"withdrawn".equals(existing.get(0).get("status"))) {
                return null;
            }
            UUID id = (UUID) existing.get(0).get("id");
            jdbc.update("update training.enrollments set status = 'enrolled', source = ?, assigned_by = ?, due_on = ?, modules_done = '{}', attempts = 0, "
                    + "best_score = null, version = version + 1 where id = ?", source, by, due == null ? null : Date.valueOf(due), id);
            return id;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into training.enrollments (id, course_id, user_id, source, assigned_by, due_on) values (?, ?, ?, ?, ?, ?)", id, courseId, userId,
                source, by, due == null ? null : Date.valueOf(due));
        return id;
    }

    private void requirePrerequisites(UUID courseId, UUID userId) {
        List<String> missing = jdbc.queryForList("select p.title from training.courses c cross join unnest(c.prerequisite_ids) as pre(id) "
                + "join training.courses p on p.id = pre.id where c.id = ? and not exists (select 1 from training.enrollments e where e.course_id = p.id "
                + "and e.user_id = ? and e.status = 'passed')", String.class, courseId, userId);
        if (!missing.isEmpty()) {
            throw ApiException.conflict("ESG-409-STATE", "Pass " + String.join(", ", missing) + " first.");
        }
    }

    // ---------------------------------------------------------------- learning

    public Enrollment get(UUID id) {
        Enrollment e = jdbc.query(SELECT + "where e.id = ?", (rs, i) -> enrollment(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Enrolment"));
        if (!e.userId().equals(CurrentUser.userIdOrNull()) && !CurrentUser.hasPermission("training:update")) {
            throw ApiException.notFound("Enrolment");
        }
        return e;
    }

    private Enrollment mine(UUID id) {
        Enrollment e = get(id);
        if (!e.userId().equals(CurrentUser.access().userId())) {
            throw ApiException.forbidden("Only the learner can do this.");
        }
        return e;
    }

    @Transactional
    public Enrollment moduleDone(UUID id, UUID moduleId, Long version) {
        Enrollment e = mine(id);
        if (!"online".equals(e.delivery())) {
            throw ApiException.conflict("ESG-409-STATE", "Classroom courses are completed by recording attendance.");
        }
        if (!java.util.Set.of("enrolled", "in_progress", "failed").contains(e.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This enrolment is " + e.status() + ".");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from training.course_modules where id = ? and course_id = ?)", Boolean.class,
                moduleId, e.courseId()))) {
            throw FieldValidationException.of("moduleId", "must be a module of the course");
        }
        int n = jdbc.update("update training.enrollments set modules_done = case when ? = any(modules_done) then modules_done else array_append(modules_done, ?) end, "
                + "status = case when status = 'enrolled' then 'in_progress' else status end, version = version + 1 where id = ? and version = ?",
                moduleId, moduleId, id, version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "Your progress was saved elsewhere. Reload and try again.");
        }
        Enrollment after = get(id);
        Integer questions = jdbc.queryForObject("select count(*) from training.quiz_questions where course_id = ?", Integer.class, e.courseId());
        if (questions != null && questions == 0 && after.modulesDone().size() >= after.modulesTotal()) {
            complete(after, null);
            return get(id);
        }
        return after;
    }

    /** The quiz: all modules first; unlimited attempts, best score kept; passing completes the course. */
    @Transactional
    public Enrollment submitQuiz(UUID id, QuizRequest r) {
        Enrollment e = mine(id);
        if (!"online".equals(e.delivery())) {
            throw ApiException.conflict("ESG-409-STATE", "Classroom courses are completed by recording attendance.");
        }
        if ("passed".equals(e.status()) || "withdrawn".equals(e.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This enrolment is " + e.status() + ".");
        }
        if (e.modulesDone().size() < e.modulesTotal()) {
            throw ApiException.conflict("ESG-409-STATE", "Finish all " + e.modulesTotal() + " modules before the quiz.");
        }
        List<Integer> correct = jdbc.queryForList("select correct_index from training.quiz_questions where course_id = ? order by sort_order", Integer.class,
                e.courseId());
        if (r.answers().size() != correct.size()) {
            throw FieldValidationException.of("answers", "answer all " + correct.size() + " questions");
        }
        int score = TrainingMath.score(r.answers(), correct);
        int passing = jdbc.queryForObject("select passing_score from training.courses where id = ?", Integer.class, e.courseId());
        boolean passed = TrainingMath.passed(score, passing);
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into training.quiz_attempts (enrollment_id, answers, score, passed) values (?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setArray(2, con.createArrayOf("int4", r.answers().stream().map(a -> a == null ? -1 : a).toArray()));
            ps.setInt(3, score);
            ps.setBoolean(4, passed);
            return ps;
        });
        int n = jdbc.update("update training.enrollments set attempts = attempts + 1, best_score = greatest(coalesce(best_score, 0), ?), status = ?, "
                + "version = version + 1 where id = ? and version = ?", score, passed ? "in_progress" : "failed", id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "Your progress was saved elsewhere. Reload and try again.");
        }
        if (passed) {
            complete(get(id), score);
        }
        return get(id);
    }

    /** Classroom courses: a training admin records attendance (period-locked by the date). */
    @Transactional
    public Enrollment recordAttendance(UUID id, AttendanceRequest r) {
        Enrollment e = get(id);
        if (!"classroom".equals(e.delivery())) {
            throw ApiException.conflict("ESG-409-STATE", "Online courses are completed by the learner.");
        }
        if ("passed".equals(e.status()) || "withdrawn".equals(e.status())) {
            throw ApiException.conflict("ESG-409-STATE", "This enrolment is " + e.status() + ".");
        }
        if (r.completedOn().isAfter(today())) {
            throw FieldValidationException.of("completedOn", "can't be in the future");
        }
        if (r.score() != null && (r.score() < 0 || r.score() > 100)) {
            throw FieldValidationException.of("score", "must be 0 to 100");
        }
        if (!r.passed()) {
            update("update training.enrollments set status = 'failed', best_score = ?, recorded_by = ?, version = version + 1 where id = ? and version = ?",
                    r.score(), CurrentUser.access().userId(), id, r.version());
            return get(id);
        }
        update("update training.enrollments set status = 'passed', completed_on = ?, hours = ?, best_score = ?, recorded_by = ?, version = version + 1 "
                + "where id = ? and version = ?", Date.valueOf(r.completedOn()), r.hours(), r.score(), CurrentUser.access().userId(), id, r.version());
        issue(get(id), r.score(), r.completedOn());
        return get(id);
    }

    @Transactional
    public Enrollment withdraw(UUID id, Long version) {
        Enrollment e = get(id);
        if ("passed".equals(e.status())) {
            throw ApiException.conflict("ESG-409-STATE", "A completed course stays on record.");
        }
        update("update training.enrollments set status = 'withdrawn', version = version + 1 where id = ? and version = ?", id, version);
        return get(id);
    }

    private void update(String sql, Object... args) {
        if (jdbc.update(sql, args) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This enrolment was changed by someone else. Reload and try again.");
        }
    }

    private void complete(Enrollment e, Integer score) {
        LocalDate today = today();
        jdbc.update("update training.enrollments set status = 'passed', completed_on = ?, hours = ?, version = version + 1 where id = ?", Date.valueOf(today),
                e.durationHours(), e.id());
        issue(get(e.id()), score, today);
    }

    private void issue(Enrollment e, Integer score, LocalDate on) {
        Integer validity = jdbc.queryForObject("select certificate_validity_months from training.courses where id = ?", Integer.class, e.courseId());
        String code = TrainingMath.newCode();
        while (Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from training.certificate_by_code(?))", Boolean.class, code))) {
            code = TrainingMath.newCode();
        }
        LocalDate expires = TrainingMath.expiry(on, validity);
        jdbc.update("insert into training.certificates (enrollment_id, user_id, course_id, code, holder_name, course_title, score, issued_on, expires_on) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)", e.id(), e.userId(), e.courseId(), code, e.userName(), e.courseTitle(), score, Date.valueOf(on),
                expires == null ? null : Date.valueOf(expires));
        notifications.notifyAfterCommit(e.userId(), "TRAINING_CERTIFICATE", Map.of("course", e.courseTitle(), "code", code, "link", verifyUrl(code)));
    }

    private void notifyAssigned(UUID user, String title, BigDecimal hours, LocalDate due) {
        notifications.notifyAfterCommit(user, "TRAINING_ASSIGNED", Map.of("course", title, "hours", hours.stripTrailingZeros().toPlainString(),
                "due", due == null ? "" : ", due " + due));
    }

    // ---------------------------------------------------------------- progress and competency (TRD GET /training/progress)

    public Progress myProgress() {
        UUID me = CurrentUser.access().userId();
        List<Enrollment> list = jdbc.query(SELECT + "where e.user_id = ? and e.status <> 'withdrawn' order by e.status = 'passed', e.due_on nulls last, c.title",
                (rs, i) -> enrollment(rs), me);
        Person p = people().stream().filter(x -> x.id().equals(me)).findFirst().orElse(new Person(me, "", "", List.of()));
        return new Progress(list, gaps(p), hours(me, today().getYear()));
    }

    public List<Enrollment> enrollments(UUID courseId, String status) {
        StringBuilder where = new StringBuilder("where e.status <> 'withdrawn'");
        List<Object> args = new ArrayList<>();
        if (courseId != null) {
            where.append(" and e.course_id = ?");
            args.add(courseId);
        }
        if (status != null) {
            where.append(" and e.status = ?");
            args.add(status);
        }
        return jdbc.query(SELECT + where + " order by c.title, user_name", (rs, i) -> enrollment(rs), args.toArray());
    }

    public List<TeamRow> team() {
        int year = today().getYear();
        Map<UUID, int[]> counts = new HashMap<>();
        jdbc.query("select user_id, count(*) filter (where status = 'passed'), count(*) filter (where status <> 'passed' and due_on < ?) "
                + "from training.enrollments where status <> 'withdrawn' group by user_id", rs -> {
                    counts.put(rs.getObject(1, UUID.class), new int[] {rs.getInt(2), rs.getInt(3)});
                }, Date.valueOf(today()));
        List<TeamRow> out = new ArrayList<>();
        for (Person p : people()) {
            if (p.roles().isEmpty()) {
                continue;
            }
            List<Gap> gaps = gaps(p);
            int required = requiredCourses(p).size();
            int[] c = counts.getOrDefault(p.id(), new int[] {0, 0});
            out.add(new TeamRow(p.id(), p.name(), p.roles(), required, c[0], c[1], hours(p.id(), year), gaps));
        }
        return out;
    }

    private Map<UUID, String> requiredCourses(Person p) {
        Map<UUID, String> required = new LinkedHashMap<>();
        for (Path path : courses.paths()) {
            if ("active".equals(path.status()) && path.targetRole() != null && p.roles().contains(path.targetRole())) {
                path.courseIds().forEach(c -> required.putIfAbsent(c, path.name()));
            }
        }
        return required;
    }

    /** Courses of the person's role paths not passed, or whose certificate has expired or been revoked. */
    List<Gap> gaps(Person p) {
        Map<UUID, String> required = requiredCourses(p);
        if (required.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> state = new HashMap<>();
        jdbc.query("select e.course_id, e.status, c.expires_on, c.revoked_at is not null from training.enrollments e left join training.certificates c "
                + "on c.enrollment_id = e.id where e.user_id = ?", rs -> {
                    String s = rs.getString(2);
                    if ("passed".equals(s)) {
                        s = TrainingMath.status(rs.getObject(3, LocalDate.class), rs.getBoolean(4), today());
                    }
                    state.put(rs.getObject(1, UUID.class), s);
                }, p.id());
        Map<UUID, String> titles = new HashMap<>();
        jdbc.query("select id, title from training.courses where id = any(?::uuid[])", rs -> {
            titles.put(rs.getObject(1, UUID.class), rs.getString(2));
        }, (Object) required.keySet().stream().map(UUID::toString).toArray(String[]::new));
        List<Gap> out = new ArrayList<>();
        required.forEach((course, path) -> {
            String s = state.get(course);
            if (!"valid".equals(s)) {
                out.add(new Gap(course, titles.getOrDefault(course, "(removed course)"), path, s == null ? "not_enrolled" : s));
            }
        });
        return out;
    }

    BigDecimal hours(UUID user, int year) {
        return jdbc.queryForObject("select coalesce(sum(hours), 0) from training.enrollments where user_id = ? and status = 'passed' "
                + "and extract(year from completed_on) = ?", BigDecimal.class, user, year);
    }

    private Enrollment enrollment(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        LocalDate due = rs.getObject("due_on", LocalDate.class);
        Certificate cert = jdbc.query("select * from training.certificates where enrollment_id = ?", (r, i) -> certificate(r), id).stream().findFirst().orElse(null);
        return new Enrollment(id, rs.getObject("course_id", UUID.class), rs.getString("code"), rs.getString("title"), rs.getString("delivery"),
                rs.getBigDecimal("duration_hours"), rs.getObject("user_id", UUID.class), rs.getString("user_name"), rs.getString("source"), due,
                due != null && !"passed".equals(status) && due.isBefore(today()), status, CourseService.uuids(rs.getArray("modules_done")),
                rs.getInt("modules_total"), rs.getInt("attempts"), (Integer) rs.getObject("best_score"), rs.getObject("completed_on", LocalDate.class),
                rs.getBigDecimal("hours"), cert, rs.getLong("version"));
    }

    Certificate certificate(ResultSet r) throws SQLException {
        LocalDate expires = r.getObject("expires_on", LocalDate.class);
        boolean revoked = r.getTimestamp("revoked_at") != null;
        String code = r.getString("code");
        return new Certificate(r.getObject("id", UUID.class), code, r.getString("holder_name"), r.getString("course_title"), (Integer) r.getObject("score"),
                r.getObject("issued_on", LocalDate.class), expires, TrainingMath.status(expires, revoked, today()), r.getString("revoke_reason"), verifyUrl(code));
    }
}
