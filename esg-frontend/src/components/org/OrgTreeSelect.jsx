import { forwardRef } from 'react';
import { MenuItem, TextField } from '@mui/material';
import { flattenTree } from './orgTree';

/**
 * Organisation picker showing the hierarchy by indentation (TRD §7.4: org tree reused by every module as a filter).
 * `orgs`: flat list from GET /organisations. `exclude`: ids to hide (e.g. an org and its subtree when choosing a parent).
 * `emptyLabel`: label for the empty value ('' = no selection / all organisations).
 */
const OrgTreeSelect = forwardRef(function OrgTreeSelect({ orgs = [], exclude, emptyLabel, ...props }, ref) {
  const rows = flattenTree(orgs).filter(({ org }) => !exclude?.has(org.id));
  return (
    <TextField select inputRef={ref} {...props}>
      {emptyLabel !== undefined && <MenuItem value="">{emptyLabel}</MenuItem>}
      {rows.map(({ org, depth }) => (
        <MenuItem key={org.id} value={org.id} sx={{ pl: 2 + depth * 2 }}>{org.name}</MenuItem>
      ))}
    </TextField>
  );
});

export default OrgTreeSelect;
