import { useState } from 'react';
import {
  Alert, Box, Button, Card, Chip, MenuItem, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { EmptyState } from '../../../components/states/States';
import { applyProblem } from '../../../services/formErrors';
import { useFrameworksQuery } from '../../reference/referenceApi';
import { useMapFrameworksMutation } from '../complianceApi';
import { GAP_STATUS } from '../schemas';

// no-mock-gate: config — labels for mapping relations
const RELATION = { equivalent: 'Equivalent', partial: 'Partial', related: 'Related', shared_metric: 'Same data' };

/**
 * Cross-framework mapping (TRD POST /compliance/map; BRD-5.2.3-07): how what is recorded against one framework answers
 * another's requirements — enter data once, map to many.
 */
export default function MappingTab({ year }) {
  const { data: frameworks = [] } = useFrameworksQuery();
  const withContent = frameworks.filter((f) => f.requirementCount > 0);
  const [form, setForm] = useState({ sourceFramework: 'GRI', targetFramework: 'ISSB_S2' });
  const [errors, setErrors] = useState({});
  const [problem, setProblem] = useState('');
  const [map, { data, isLoading }] = useMapFrameworksMutation();
  const run = async () => {
    const local = {};
    if (!form.sourceFramework) local.sourceFramework = 'Choose a framework';
    if (!form.targetFramework) local.targetFramework = 'Choose a framework';
    if (form.sourceFramework && form.sourceFramework === form.targetFramework) local.targetFramework = 'Must differ from the source framework';
    setErrors(local);
    setProblem('');
    if (Object.keys(local).length) return;
    try {
      await map({ ...form, year }).unwrap();
    } catch (err) {
      const fields = {};
      setProblem(applyProblem(err, (name, { message }) => { fields[name] = message; }, { knownFields: ['sourceFramework', 'targetFramework'] }));
      setErrors(fields);
    }
  };
  const select = (name, label) => (
    <TextField select size="small" label={label} value={form[name]} sx={{ minWidth: 260 }} error={!!errors[name]} helperText={errors[name]}
      onChange={(e) => setForm((f) => ({ ...f, [name]: e.target.value }))}>
      {withContent.map((f) => <MenuItem key={f.code} value={f.code}>{f.name}</MenuItem>)}
    </TextField>
  );
  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        {select('sourceFramework', 'From')}
        {select('targetFramework', 'To')}
        <Button variant="contained" onClick={run} disabled={isLoading}>Map {year}</Button>
      </Box>
      {problem && <Alert severity="error" sx={{ mb: 2 }}>{problem}</Alert>}
      {data && (
        <Card>
          <Box sx={{ p: 2 }}>
            <Typography variant="subtitle2" aria-label="Mapping summary">
              {data.mappedRequirements} {data.targetFramework} requirements map from {data.sourceFramework}; {data.metViaSource} met through it in {data.year}.
            </Typography>
          </Box>
          {data.requirements.length === 0 ? <EmptyState title="No mappings between these frameworks yet" /> : (
            <TableContainer>
              <Table size="small" aria-label="Mapped requirements">
                <TableHead><TableRow>
                  <TableCell>{data.targetFramework} requirement</TableCell><TableCell>Status</TableCell><TableCell>From {data.sourceFramework}</TableCell>
                </TableRow></TableHead>
                <TableBody>
                  {data.requirements.map((r) => (
                    <TableRow key={r.code}>
                      <TableCell><strong>{r.code}</strong> {r.title}</TableCell>
                      <TableCell>
                        <Chip size="small" color={GAP_STATUS[r.status]?.color} label={GAP_STATUS[r.status]?.label ?? r.status} />
                        {r.satisfiedBy && <Typography variant="caption" display="block">via {r.satisfiedBy}</Typography>}
                      </TableCell>
                      <TableCell>
                        {r.sources.map((s) => (
                          <Box key={`${s.code}-${s.relation}`} sx={{ mb: 0.5 }}>
                            <Typography variant="body2" component="span"><strong>{s.code}</strong> {s.title} </Typography>
                            <Chip size="small" variant="outlined" label={RELATION[s.relation] ?? s.relation} sx={{ height: 20 }} />{' '}
                            {s.status && <Chip size="small" color={GAP_STATUS[s.status]?.color} label={GAP_STATUS[s.status]?.label} sx={{ height: 20 }} />}
                          </Box>
                        ))}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      )}
    </>
  );
}
