import { Chip } from '@mui/material';
import { STATUS } from '../schemas';

/** Workflow status of a data point (draft → submitted → validated → approved / rejected). */
export default function StatusChip({ status }) {
  const s = STATUS[status] ?? { label: status };
  return <Chip size="small" label={s.label} sx={{ bgcolor: s.bg, color: s.color, fontWeight: 600, fontSize: '0.65rem' }} />;
}
