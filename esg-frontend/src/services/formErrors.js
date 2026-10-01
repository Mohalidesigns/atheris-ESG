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
