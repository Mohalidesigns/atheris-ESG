import { Button, Tooltip } from '@mui/material';

/**
 * An action the user's role allows but segregation of duties forbids here (Phase 2): shown disabled with the reason the
 * server gave, instead of hidden (confusing) or enabled (fails on click). The server refuses it regardless.
 */
export default function BlockedAction({ label, reason, variant = 'contained', size = 'small' }) {
  return (
    <Tooltip title={reason} arrow>
      {/* A disabled button doesn't fire mouse events; the span keeps the tooltip working and names the reason. */}
      <span aria-label={`${label}: ${reason}`} tabIndex={0}>
        <Button size={size} variant={variant} disabled>{label}</Button>
      </span>
    </Tooltip>
  );
}
