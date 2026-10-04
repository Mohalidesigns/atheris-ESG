import { Chip, Tooltip } from '@mui/material';
import { tr } from '../../../i18n';

/**
 * S7: marks a figure calculated with an emission factor still pending expert (SME) review. Shown wherever the
 * factor's review status is known: emission records, the calculator result.
 */
export default function ProvisionalBadge({ reviewStatus, sx }) {
  if (reviewStatus !== 'pending_sme') return null;
  return (
    <Tooltip title={tr("This factor from the library hasn't been verified by an ESG expert yet. The figure is provisional; administrators can block such factors in Settings.")}>
      <Chip size="small" color="warning" variant="outlined" label={tr("Provisional factor")} sx={{ height: 18, fontSize: '0.6rem', ...sx }} />
    </Tooltip>
  );
}
