import { Chip, Tooltip } from '@mui/material';
import { AutoAwesome } from '@mui/icons-material';

/** The label every AI output carries (CLAUDE.md rule 10, D38 (5)): what produced it, and that a person decides. */
export default function AiChip({ engine, sx }) {
  return (
    <Tooltip title={`Produced by ${engine || 'the AI service'}. Check it before relying on it.`}>
      <Chip size="small" color="secondary" variant="outlined" icon={<AutoAwesome fontSize="small" />} label="AI-generated" sx={sx} />
    </Tooltip>
  );
}
