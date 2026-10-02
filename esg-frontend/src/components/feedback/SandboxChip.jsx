import { Chip } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

/**
 * The "Sandbox" label every sandboxed integration carries (CLAUDE.md rule 9), linking to Administration › Integrations
 * where its status is explained (Phase 2, item 11). `label` can say more, e.g. "Sandbox: not checked".
 */
export default function SandboxChip({ label = 'Sandbox', title, sx }) {
  return (
    <Chip size="small" color="warning" variant="outlined" label={label} title={title ?? 'Not connected to the live service — see Integrations'}
      component={RouterLink} to="/admin?tab=integrations" clickable sx={{ height: 18, fontSize: '0.6rem', ...sx }} />
  );
}
