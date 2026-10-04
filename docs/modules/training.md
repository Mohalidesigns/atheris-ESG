# M12 Training & Capacity Building (WP-42)

Route `/training` (training:read): My learning · Catalogue · Courses (training:create) · Learning paths · Team progress
and Certificates (training:update). Design: D37. Migration V034. Public page `/verify-certificate/{code}` (no session).
No course content is shipped: each organisation writes or uploads its own.

## Courses

`GET/POST /api/v1/training/courses`, `GET/PUT/DELETE /{id}`: code (A–Z, 0–9, hyphens; unique), title, description,
category, delivery (online | classroom), duration in hours (credited on completion), pass mark, certificate validity in
months (blank: no expiry), prerequisites, modules (text, PDF from the file store, or an https video link; minutes) and
multiple-choice questions (2–6 options, one correct). Content can change only while the course is a draft (DB trigger
`training.assert_course_draft`). `POST /{id}/actions`: `publish` (needs a module), `retire`, `copy` (new draft
`CODE-Vn`). Learners see published courses (and ones they are enrolled in) and never the correct answers. A course with
people enrolled can't be deleted (ESG-409-IN-USE).

## Learning paths

`GET/POST /training/paths`, `GET/PUT/DELETE /{id}` (training:configure): name, target role (a working role), published
courses in order, active | archived. People holding the role see the path's courses as required.
`POST /training/paths/assign` enrols the chosen people, or everyone with the role.

## Enrolment and completion

- `POST /training/courses/{id}/enrol`: self-enrolment in a published course once its prerequisites are passed.
- `POST /training/enrollments` (training:update): assign people with an optional due date; they are notified
  (TRAINING_ASSIGNED). A withdrawn enrolment is reopened.
- `POST /enrollments/{id}/modules/{moduleId}/done?version=`: the learner marks a module done.
- `POST /enrollments/{id}/quiz` (`{answers, version}`): after every module; score = correct ÷ questions, rounded
  half-up; pass at or above the pass mark; unlimited attempts with the best score kept. An online course without
  questions completes when the last module is done. Completion is dated today and credits the course's hours.
- `POST /enrollments/{id}/attendance` (training:update, classroom only): date (not future), hours, passed, score. Hours
  belong to the date's reporting period, so a locked period is refused (ESG-409-LOCKED).
- `POST /enrollments/{id}/withdraw?version=`: not for passed courses.

`GET /training/progress`: my enrolments, hours this year and competency gaps (required courses not passed, or with an
expired or revoked certificate). `GET /training/team` and `GET /training/enrollments` (training:update): the team matrix
and enrolments by course. `GET /training/overview`: published courses, open enrolments, completion rate, hours, hours
per person and certificates for the year.

## Certificates

Issued on passing: a 10-character code (no 0/O/1/I), holder and course names as at issue, score, issue date and expiry.
`GET /training/certificates/{id}/pdf`: one landscape page with the code and a QR code (zxing) to
`{esg.app.public-url}/verify-certificate/{code}`; the holder or training:update only. `POST /{id}/revoke` (training:configure)
with a reason. `GET /api/v1/public/certificates/{code}` (no session, throttled per client like the other public
endpoints): status (valid | expired | revoked), holder, course, organisation, issue and expiry dates — nothing else.

## Links

Social's `training_hours_per_employee` (GRI 404-1) = hours passed in the period ÷ approved headcount. Report variables
`{{training:hours_total|hours_per_employee|completion_rate|certificates_issued}}`; dashboard Training card
(training:update). Roles: every working role reads; TRAINING_ADMIN manages everything; ESG_DIRECTOR assigns, records
attendance and sees team progress. Open (D37): video streaming, ICAN/CIBN/FRCN accreditation, LTI, case-study
assessments, webinar hosting.
