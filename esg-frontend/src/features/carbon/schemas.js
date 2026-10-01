import { z } from 'zod';

/** Mirrors ReferenceDtos.CustomFactorRequest + EmissionFactorService rules (esg-backend, WP-17). */
export const REGION = /^([A-Z]{2}|WEST_AFRICA|EAST_AFRICA|SOUTHERN_AFRICA|NORTH_AFRICA|CENTRAL_AFRICA|GLOBAL)$/;
export const CATEGORY = /^[a-z][a-z0-9_]*$/;
const FACTOR = /^\d{1,10}(\.\d{1,10})?$/; // @Digits(integer = 10, fraction = 10)

export const emptyFactor = {
  region: 'NG', category: '', subCategory: '', factorValue: '', unit: '', source: '', validFrom: '', validTo: '',
};

export const customFactorSchema = z
  .object({
    region: z.string().trim().regex(REGION, 'Use an ISO country code (e.g. NG) or a region such as WEST_AFRICA'),
    category: z.string().trim().min(1, 'Category is required').max(100, 'At most 100 characters')
      .regex(CATEGORY, 'Lower-case letters, digits and underscores, e.g. diesel_generator'),
    subCategory: z.string().trim().max(200, 'At most 200 characters'),
    factorValue: z.string().trim().min(1, 'Value is required')
      .regex(FACTOR, 'Enter a positive number with up to 10 decimal places')
      .refine((v) => Number(v) > 0, 'Must be greater than 0')
      .refine((v) => Number(v) <= 1000000, 'At most 1,000,000'),
    unit: z.string().trim().min(1, 'Unit is required').max(50, 'At most 50 characters'),
    source: z.string().trim().min(1, 'Say where this value comes from').max(255, 'At most 255 characters'),
    validFrom: z.string().min(1, 'Start date is required'),
    validTo: z.string(),
  })
  .refine((v) => !v.validTo || !v.validFrom || v.validTo >= v.validFrom, { path: ['validTo'], message: 'Must be on or after the start date' });

export const toFactorBody = (v) => ({
  region: v.region.trim(),
  category: v.category.trim(),
  subCategory: v.subCategory.trim() || null,
  factorValue: Number(v.factorValue),
  unit: v.unit.trim(),
  source: v.source.trim(),
  validFrom: v.validFrom,
  validTo: v.validTo || null,
});

export const toFactorForm = (f) => ({
  region: f.region, category: f.category, subCategory: f.subCategory ?? '', factorValue: String(f.factorValue),
  unit: f.unit, source: f.source, validFrom: f.validFrom, validTo: f.validTo ?? '',
});
