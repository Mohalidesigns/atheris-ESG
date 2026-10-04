package com.esg.training;

import com.esg.exception.FieldValidationException;
import com.esg.model.dto.ApiResponse;
import com.esg.training.TrainingDtos.ActionRequest;
import com.esg.training.TrainingDtos.AssignPathRequest;
import com.esg.training.TrainingDtos.AssignRequest;
import com.esg.training.TrainingDtos.AssignResult;
import com.esg.training.TrainingDtos.AttendanceRequest;
import com.esg.training.TrainingDtos.Certificate;
import com.esg.training.TrainingDtos.Course;
import com.esg.training.TrainingDtos.CourseRequest;
import com.esg.training.TrainingDtos.Enrollment;
import com.esg.training.TrainingDtos.Overview;
import com.esg.training.TrainingDtos.Path;
import com.esg.training.TrainingDtos.PathRequest;
import com.esg.training.TrainingDtos.Person;
import com.esg.training.TrainingDtos.Progress;
import com.esg.training.TrainingDtos.QuizRequest;
import com.esg.training.TrainingDtos.TeamRow;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M12 Training (WP-42, D37; TRD §6.4.2 Training, §8.12): courses with modules and quizzes, learning paths, enrolments,
 * progress and competency gaps, classroom attendance and certificates.
 */
@RestController
@RequestMapping("/v1/training")
public class TrainingController {

    private final CourseService courses;
    private final EnrollmentService enrollments;
    private final CertificateService certificates;
    private final TrainingInsights insights;

    public TrainingController(CourseService courses, EnrollmentService enrollments, CertificateService certificates, TrainingInsights insights) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.certificates = certificates;
        this.insights = insights;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(required = false) Integer year) {
        int now = enrollments.today().getYear();
        int y = year == null ? now : year;
        if (y < 2000 || y > now) {
            throw FieldValidationException.of("year", "must be between 2000 and this year");
        }
        return ResponseEntity.ok(ApiResponse.ok(insights.overview(y)));
    }

    // ---------------------------------------------------------------- courses (TRD GET /training/courses)

    @GetMapping("/courses")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<List<Course>>> courses(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(courses.list(status)));
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Course>> course(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(courses.get(id)));
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAuthority('training:create')")
    public ResponseEntity<ApiResponse<Course>> createCourse(@Valid @RequestBody CourseRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(courses.create(r)));
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('training:create')")
    public ResponseEntity<ApiResponse<Course>> updateCourse(@PathVariable UUID id, @Valid @RequestBody CourseRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(courses.update(id, r)));
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('training:create')")
    public ResponseEntity<Void> deleteCourse(@PathVariable UUID id) {
        courses.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/courses/{id}/actions")
    @PreAuthorize("hasAuthority('training:create')")
    public ResponseEntity<ApiResponse<Course>> courseAction(@PathVariable UUID id, @Valid @RequestBody ActionRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(courses.act(id, r)));
    }

    // ---------------------------------------------------------------- learning paths

    @GetMapping("/paths")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<List<Path>>> paths() {
        return ResponseEntity.ok(ApiResponse.ok(courses.paths()));
    }

    @GetMapping("/paths/{id}")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Path>> path(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(courses.path(id)));
    }

    @PostMapping("/paths")
    @PreAuthorize("hasAuthority('training:configure')")
    public ResponseEntity<ApiResponse<Path>> createPath(@Valid @RequestBody PathRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(courses.createPath(r)));
    }

    @PutMapping("/paths/{id}")
    @PreAuthorize("hasAuthority('training:configure')")
    public ResponseEntity<ApiResponse<Path>> updatePath(@PathVariable UUID id, @Valid @RequestBody PathRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(courses.updatePath(id, r)));
    }

    @DeleteMapping("/paths/{id}")
    @PreAuthorize("hasAuthority('training:configure')")
    public ResponseEntity<Void> deletePath(@PathVariable UUID id) {
        courses.deletePath(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- enrolments (TRD POST /training/enrollments)

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<List<Person>>> people() {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.people()));
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<List<Enrollment>>> enrollments(@RequestParam(required = false) UUID courseId,
                                                                     @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.enrollments(courseId, status)));
    }

    @GetMapping("/enrollments/{id}")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Enrollment>> enrollment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.get(id)));
    }

    @PostMapping("/courses/{id}/enrol")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Enrollment>> enrol(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(enrollments.enrolSelf(id), "Enrolled"));
    }

    @PostMapping("/enrollments")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<AssignResult>> assign(@Valid @RequestBody AssignRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(enrollments.assign(r)));
    }

    @PostMapping("/paths/assign")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<AssignResult>> assignPath(@Valid @RequestBody AssignPathRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(enrollments.assignPath(r)));
    }

    @PostMapping("/enrollments/{id}/modules/{moduleId}/done")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Enrollment>> moduleDone(@PathVariable UUID id, @PathVariable UUID moduleId, @RequestParam Long version) {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.moduleDone(id, moduleId, version)));
    }

    @PostMapping("/enrollments/{id}/quiz")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Enrollment>> quiz(@PathVariable UUID id, @Valid @RequestBody QuizRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.submitQuiz(id, r)));
    }

    @PostMapping("/enrollments/{id}/attendance")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<Enrollment>> attendance(@PathVariable UUID id, @Valid @RequestBody AttendanceRequest r) {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.recordAttendance(id, r), "Attendance recorded"));
    }

    @PostMapping("/enrollments/{id}/withdraw")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<Enrollment>> withdraw(@PathVariable UUID id, @RequestParam Long version) {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.withdraw(id, version)));
    }

    // ---------------------------------------------------------------- progress (TRD GET /training/progress)

    @GetMapping("/progress")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<ApiResponse<Progress>> progress() {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.myProgress()));
    }

    @GetMapping("/team")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<List<TeamRow>>> team() {
        return ResponseEntity.ok(ApiResponse.ok(enrollments.team()));
    }

    // ---------------------------------------------------------------- certificates

    @GetMapping("/certificates")
    @PreAuthorize("hasAuthority('training:update')")
    public ResponseEntity<ApiResponse<List<Certificate>>> certificates() {
        return ResponseEntity.ok(ApiResponse.ok(certificates.list()));
    }

    @GetMapping("/certificates/{id}/pdf")
    @PreAuthorize("hasAuthority('training:read')")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable UUID id) throws IOException {
        Certificate c = certificates.get(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("certificate-" + c.code() + ".pdf").build().toString())
                .body(certificates.pdf(id));
    }

    @PostMapping("/certificates/{id}/revoke")
    @PreAuthorize("hasAuthority('training:configure')")
    public ResponseEntity<ApiResponse<Certificate>> revoke(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(certificates.revoke(id, body.get("note")), "Certificate revoked"));
    }
}
