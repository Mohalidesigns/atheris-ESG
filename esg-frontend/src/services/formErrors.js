/**
 * Puts server field errors ({ "assignments[0].orgScopeId": "…" }) onto React Hook Form fields
 * ("assignments.0.orgScopeId"). Returns true if at least one field error was applied.
 */
export function applyServerFieldErrors(setError, fieldErrors) {
  const entries = Object.entries(fieldErrors ?? {});
  entries.forEach(([field, message]) => {
    setError(field.replace(/\[(\d+)\]/g, '.$1'), { type: 'server', message });
  });
  return entries.length > 0;
}

/**
 * Handles a failed submit: RFC 7807 field errors go onto the matching inputs (server names can be renamed with
 * `fieldMap`, e.g. { 'admin.password': 'adminPassword' }); returns the message to show above the form, or ''
 * when every problem landed on a field.
 */
export function applyProblem(error, setError, { fieldMap = {}, knownFields } = {}) {
  const body = error?.data;
  const fieldErrors = body?.errors && typeof body.errors === 'object' ? body.errors : {};
  const unplaced = [];
  Object.entries(fieldErrors).forEach(([serverField, message]) => {
    const field = (fieldMap[serverField] ?? serverField).replace(/\[(\d+)\]/g, '.$1');
    if (knownFields && !knownFields.includes(field.split('.')[0])) unplaced.push(`${serverField}: ${message}`);
    else setError(field, { type: 'server', message });
  });
  if (Object.keys(fieldErrors).length > 0 && unplaced.length === 0) return '';
  const fallback = error?.status === 'FETCH_ERROR' ? 'Cannot reach the server' : 'Something went wrong';
  const headline = body?.detail || body?.title || fallback;
  return unplaced.length ? `${headline}: ${unplaced.join('; ')}` : headline;
}
