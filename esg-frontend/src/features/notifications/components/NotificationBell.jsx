import { useState } from 'react';
import {
  Alert, Badge, Box, Button, CircularProgress, IconButton, List, ListItemButton, ListItemText, Popover, Tooltip,
  Typography,
} from '@mui/material';
import { NotificationsOutlined, Tune } from '@mui/icons-material';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  useListNotificationsQuery, useMarkAllNotificationsReadMutation, useMarkNotificationReadMutation, useUnreadCountQuery,
} from '../notificationsApi';
import { toFormError } from '../../../services/apiSlice';
import NotificationPreferencesDialog from './NotificationPreferencesDialog';
import { tr, formattingLocale } from '../../../i18n';
import { gray } from '../../../theme/tokens';

const POLL_MS = 60_000;

/** Top-bar bell: unread count (polled), latest notifications, mark read, and channel preferences. */
export default function NotificationBell() {
  const navigate = useNavigate();
  // Phase 2: the popover belongs to the page it was opened on, so it is closed after any page change (Escape and
  // clicking away close it too: MUI Popover).
  const { pathname } = useLocation();
  const [opened, setOpened] = useState({ anchor: null, pathname });
  const anchor = opened.pathname === pathname ? opened.anchor : null;
  const setAnchor = (el) => setOpened({ anchor: el, pathname });
  const [prefsOpen, setPrefsOpen] = useState(false);
  const { data: unread = 0 } = useUnreadCountQuery(undefined, { pollingInterval: POLL_MS, skipPollingIfUnfocused: true });
  const list = useListNotificationsQuery({ size: 10 }, { skip: !anchor });
  const [markRead] = useMarkNotificationReadMutation();
  const [markAll, { isLoading: markingAll }] = useMarkAllNotificationsReadMutation();

  const open = async (n) => {
    if (!n.read) await markRead(n.id);
    if (n.link) {
      setAnchor(null);
      navigate(n.link);
    }
  };

  return (
    <>
      <Tooltip title={tr("Notifications")}>
        <IconButton sx={{ p: 1 }} aria-label={unread ? `Notifications, ${unread} unread` : tr("Notifications")}
          onClick={(e) => setAnchor(e.currentTarget)}>
          <Badge badgeContent={unread} color="error" max={9}>
            <NotificationsOutlined sx={{ color: gray[500], fontSize: 22 }} />
          </Badge>
        </IconButton>
      </Tooltip>
      <Popover open={Boolean(anchor)} anchorEl={anchor} onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { width: 380, maxHeight: 480 } } }}>
        {/* D47: a Thirdline card — header row on a gray-100 rule, unread rows on blue-50. */}
        <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1.5, borderBottom: `1px solid ${gray[100]}` }}>
          <Typography variant="h6" component="h2" sx={{ flex: 1 }}>{tr("Notifications")}</Typography>
          <Button size="small" disabled={!unread || markingAll} onClick={() => markAll()}>{tr("Mark all read")}</Button>
          <IconButton size="small" aria-label={tr("Notification preferences")} onClick={() => setPrefsOpen(true)}>
            <Tune fontSize="small" />
          </IconButton>
        </Box>
        {list.isFetching && !list.data && <Box sx={{ p: 3, textAlign: 'center' }}><CircularProgress size={22} /></Box>}
        {list.isError && <Alert severity="error" sx={{ m: 1 }}>{tr(toFormError(list.error).message)}</Alert>}
        {list.data && list.data.content.length === 0 && (
          <Typography sx={{ p: 3, textAlign: 'center' }} variant="body2" color="text.secondary">{tr("You're all caught up.")}</Typography>
        )}
        {list.data && list.data.content.length > 0 && (
          <List dense disablePadding>
            {list.data.content.map((n) => (
              <ListItemButton key={n.id} onClick={() => open(n)} divider sx={{ alignItems: 'flex-start', px: 2, py: 1.25, borderColor: gray[100], bgcolor: n.read ? undefined : '#EFF6FF' }}>
                <ListItemText
                  primary={<Typography variant="body2" sx={{ fontWeight: n.read ? 500 : 600, color: gray[800] }}>{tr(n.title)}</Typography>}
                  secondary={<>
                    <Typography component="span" variant="caption" sx={{ display: 'block', color: gray[600] }}>{tr(n.message)}</Typography>
                    <Typography component="span" variant="caption" color="text.secondary">{new Date(n.createdAt).toLocaleString(formattingLocale())}</Typography>
                  </>} />
              </ListItemButton>
            ))}
          </List>
        )}
      </Popover>
      <NotificationPreferencesDialog key={`prefs-${prefsOpen}`} open={prefsOpen} onClose={() => setPrefsOpen(false)} />
    </>
  );
}
