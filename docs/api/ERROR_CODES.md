# API error codes

Every error is an RFC 7807 problem (`application/problem+json`, TRD §12.3) with `title`, `status`, `detail`, `instance`,
an `errorCode` and, for field validation, `errors` (`{field: message}`). The UI shows `detail`; it never parses it.

**Audited** means the refusal is written to the audit trail as `action = denied`, with the user, the API call, the
target record id (the last id in the path) and the code (S3). Malware uploads have their own `quarantined` row.

| Code | HTTP | Meaning | Audited |
|---|---|---|---|
| `ESG-400` | 400 | Malformed request (bad JSON, wrong parameter type, missing parameter); `errors` names the parameter | |
| `ESG-400-VALIDATION` | 400 | One or more fields are invalid; `errors` has a message per field (mirrors the UI's Zod schemas) | |
| `ESG-400-RANGE` | 400 | A database CHECK refused a value (outside the allowed range) | |
| `ESG-400-REFERENCE` | 400 | A referenced record doesn't exist or is still in use | |
| `ESG-400-SELF` | 400 | You can't do this to your own account (deactivate yourself, remove your own role-assignment right) | ✅ |
| `ESG-400-TIMEZONE` | 400 | Not an IANA time zone (Settings › Reporting time zone; `errors.reportingTimezone`, F10) | |
| `ESG-400-CONTENT-DATES` | 400 | A framework content version can't take effect before the framework's current version (Phase 3) | |
| `ESG-400-EXPORT-TOO-LARGE` | 400 | More audit entries match than one CSV export holds (50,000); narrow the filters (Phase 3) | |
| `ESG-400-MFA` | 400 | MFA verification without MFA set up | |
| `ESG-401` | 401 | Not signed in, or invalid/expired credentials or refresh token | |
| `ESG-401-MFA-REQUIRED` | 401 | Your role requires two-factor authentication and this session wasn't verified with a code; sign in again (S1) | |
| `ESG-403` | 403 | Missing permission, organisation outside your scope, or module not in the subscription | ✅ |
| `ESG-403-SOD` | 403 | Segregation of duties (Data Hub): you created/submitted this data point, or you validated it and can't also approve | ✅ |
| `ESG-403-ROLE` | 403 | You can't assign a role with permissions you don't hold (no privilege escalation) | ✅ |
| `ESG-404` | 404 | Not found (also for records in another tenant or, for tenant admins, platform users) | |
| `ESG-409-VERSION` | 409 | Changed by someone else since you loaded it (optimistic locking) | |
| `ESG-409-STATE` | 409 | Not allowed in the record's current state (e.g. editing an approved report) | |
| `ESG-409-LOCKED` | 409 | The reporting period is locked (also: course content changed after publishing, WP-42) | ✅ |
| `ESG-409-SOD` | 409 | Segregation of duties (reports, regulatory changes, policies, social records, materiality assessments, supplier corrective actions, anomaly explanations): submitter can't approve; assessor can't close; whoever records incident findings or resolves a grievance can't close it (WP-33); whoever explains an anomaly flag can't accept the explanation (WP-35); whoever generates an investor scorecard can't publish it (WP-40); whoever requests a carbon credit retirement can't approve it (WP-41) | ✅ |
| `ESG-409-SERIAL` | 409 | Carbon credit serials already held in another lot or already retired/reserved — in any tenant; the message never says who holds them (WP-41) | ✅ |
| `ESG-409-CONTENT-VERSION` | 409 | This framework already has a content version with that label; use a new label (Phase 3) | |
| `ESG-409-DUPLICATE` | 409 | A record with these values already exists | |
| `ESG-409-IN-USE` | 409 | Still referenced (e.g. organisation with data) | |
| `ESG-409-SEATS` | 409 | The subscription's user limit is reached | ✅ |
| `ESG-409-MFA` | 409 | MFA already enabled; an administrator must reset it | |
| `ESG-409-FACTOR-IN-USE` | 409 | A custom emission factor already used by a calculation can't change | ✅ |
| `ESG-409-FACTOR-PENDING` | 409 | The emission factor is pending expert review and the tenant blocks provisional factors (S7) | ✅ |
| `ESG-409-SCAN-PENDING` | 409 | The file is waiting for its virus scan; it can't be downloaded or imported yet (S6) | |
| `ESG-409-QUARANTINED` | 409 | The file failed a virus scan and is quarantined (S6) | ✅ |
| `ESG-413` | 413 | File larger than the upload limit (50 MB) | |
| `ESG-415` | 415 | File type not allowed, or content doesn't match the extension | |
| `ESG-422-MALWARE` | 422 | The upload failed the virus scan and was quarantined, not stored (S6) | `quarantined` row |
| `ESG-422-AI-INPUT` | 422 | The AI service refused the input: too little history to forecast, an unreadable or scanned PDF (WP-50) | |
| `ESG-422-WATERMARK` | 422 | A PDF in a watermarked data room or a shared report can't be read to stamp the viewer's name, so it isn't handed out (WP-40) | |
| `ESG-429-RATE-LIMIT` | 429 | Too many anonymous whistleblower submissions or status checks, supplier questionnaire link calls, or public certificate checks, from this client; try later (WP-34, WP-31, WP-42) | |
| `ESG-503-AI-UNAVAILABLE` | 503 | The AI service isn't configured or reachable, or its language model isn't loaded; nothing is estimated in its place (WP-50, D38) | |
| `ESG-500` | 500 | Unexpected error (details only in the server log) | |
