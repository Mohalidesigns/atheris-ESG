import { Box, List, ListItem, ListItemText } from '@mui/material';
import { NotYetAvailable, PageHeader } from './States';
import { tr } from '../../i18n';

/** A whole module with no backend yet: the page keeps its place in the app and says what's coming and when. */
export default function ModuleNotYetAvailable({ title, subtitle, plannedIn, capabilities, note }) {
  return (
    <Box>
      <PageHeader title={title} subtitle={subtitle} />
      <NotYetAvailable title={`${title} is being built`} plannedIn={plannedIn}>
        {note ?? tr("This module has no live data yet. Nothing on this page is sample data; it will fill in as the module is built.")}
      </NotYetAvailable>
      {capabilities?.length > 0 && (
        <List dense aria-label={tr("Planned capabilities")} sx={{ mt: 1 }}>
          {capabilities.map((c) => (
            <ListItem key={c} disableGutters><ListItemText primary={`• ${c}`} /></ListItem>
          ))}
        </List>
      )}
    </Box>
  );
}
