import { Chip } from '@mui/material';
import { STATUS } from '../schemas';
import { tr } from '../../../i18n';

/** Workflow status of a data point (draft → submitted → validated → approved / rejected). */
export default function StatusChip({ status }) {
  const s = tr(STATUS[status]) ?? { label: status };
  return <Chip size="small" label={tr(s.label)} sx={{ bgcolor: s.bg, color: s.color, fontWeight: 600, fontSize: '0.65rem' }} />;
}
