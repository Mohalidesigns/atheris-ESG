/**
 * Small helpers used by the app shell. Kept free of zod and feature code so the first page load stays small (WP-53:
 * importing them from a feature's schemas.js pulled zod into the entry bundle).
 */
export const problemText = (err) => err?.data?.detail ?? Object.values(err?.data?.errors ?? {})[0] ?? 'Something went wrong';

export const saveBlob = (blobData, name) => {
  const href = URL.createObjectURL(blobData);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  a.rel = 'noopener';
  a.click();
  setTimeout(() => URL.revokeObjectURL(href), 10_000);
};
