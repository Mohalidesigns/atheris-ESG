import { z } from 'zod';

/** Phase 3: header of a framework content CSV (mirrors FrameworkContent.HEADER on the server). */
export const CONTENT_HEADER = 'requirement_code,title,category,data_type,parent_code,source_ref,metric_keys,description';

export const emptyContentImport = {
  code: '', file: null, versionLabel: '', effectiveFrom: '', effectiveTo: '', source: '', smeVerified: false, verifiedBy: '',
};

/** Mirrors FrameworkContentService.importContent's request checks; the file's rows are checked by the server. */
export const contentImportSchema = z.object({
  code: z.string().min(1, 'Choose a framework'),
  file: z.custom((f) => f instanceof File, 'Choose a .csv or .json file')
    .refine((f) => /\.(csv|json)$/i.test(f?.name ?? ''), 'Upload a .csv or .json file'),
  versionLabel: z.string().trim().min(1, 'A version label is required').max(50, 'At most 50 characters'),
  effectiveFrom: z.string().min(1, 'The effective date is required'),
  effectiveTo: z.string(),
  source: z.string().trim().min(1, 'Name the instrument this content comes from').max(500, 'At most 500 characters'),
  smeVerified: z.boolean(),
  verifiedBy: z.string().trim().max(255, 'At most 255 characters'),
}).superRefine((v, ctx) => {
  if (v.effectiveTo && v.effectiveFrom && v.effectiveTo < v.effectiveFrom) {
    ctx.addIssue({ code: 'custom', path: ['effectiveTo'], message: 'Must be on or after the effective date' });
  }
  if (v.smeVerified && !v.verifiedBy) ctx.addIssue({ code: 'custom', path: ['verifiedBy'], message: 'Say who verified this content' });
});

export const toContentImportBody = (v, dryRun) => ({
  code: v.code, file: v.file, versionLabel: v.versionLabel.trim(), effectiveFrom: v.effectiveFrom, effectiveTo: v.effectiveTo || null,
  source: v.source.trim(), smeVerified: v.smeVerified, verifiedBy: v.smeVerified ? v.verifiedBy.trim() : null, dryRun,
});
