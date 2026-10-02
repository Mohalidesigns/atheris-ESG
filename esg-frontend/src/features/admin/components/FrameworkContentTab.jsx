import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, FormControlLabel, FormHelperText, Grid, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { Download, UploadFile } from '@mui/icons-material';
import { FormDateField, FormSelect, FormTextField } from '../../../components/form/FormFields';
import { applyProblem } from '../../../services/formErrors';
import { useContentVersionsQuery, useFrameworksQuery, useImportFrameworkContentMutation } from '../../reference/referenceApi';
import { CONTENT_HEADER, contentImportSchema, emptyContentImport, toContentImportBody } from '../frameworkContentSchema';

function Versions({ code }) {
  const { data = [] } = useContentVersionsQuery(code, { skip: !code });
  if (!code) return null;
  return (
    <Card sx={{ mt: 2 }}>
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>Content versions</Typography>
        {data.length === 0 ? <Typography variant="body2" color="text.secondary">No content imported for this framework yet.</Typography> : (
          <TableContainer>
            <Table size="small" aria-label="Content versions">
              <TableHead><TableRow>
                <TableCell>Version</TableCell><TableCell>Effective</TableCell><TableCell>Status</TableCell><TableCell>Source</TableCell>
                <TableCell align="right">Rows</TableCell><TableCell>Changes</TableCell><TableCell>Imported</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.map((v) => (
                  <TableRow key={v.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{v.versionLabel}</TableCell>
                    <TableCell>{v.effectiveFrom}{v.effectiveTo ? ` – ${v.effectiveTo}` : ''}</TableCell>
                    <TableCell>
                      <Chip size="small" color={v.smeVerified ? 'success' : 'default'}
                        label={v.smeVerified ? `SME verified (${v.verifiedBy})` : 'Pending SME verification'} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 280 }}>{v.source}</TableCell>
                    <TableCell align="right">{v.rows}</TableCell>
                    <TableCell>+{v.added} · ~{v.changed} · −{v.retired}</TableCell>
                    <TableCell>{v.importedBy} · {new Date(v.importedAt).toLocaleDateString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}

function Outcome({ result }) {
  if (result.errors.length > 0) {
    return (
      <Box aria-label="Import check">
        <Alert severity="error" sx={{ mb: 1 }}>The file has {result.errors.length} problem{result.errors.length === 1 ? '' : 's'}; nothing was imported.</Alert>
        <TableContainer>
          <Table size="small" aria-label="File problems">
            <TableHead><TableRow><TableCell>Line</TableCell><TableCell>Column</TableCell><TableCell>Problem</TableCell></TableRow></TableHead>
            <TableBody>
              {result.errors.map((e, i) => (
                <TableRow key={`${e.line}-${i}`}><TableCell>{e.line || '—'}</TableCell><TableCell>{e.field ?? '—'}</TableCell><TableCell>{e.message}</TableCell></TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    );
  }
  const list = (codes) => (codes.length > 12 ? `${codes.slice(0, 12).join(', ')} and ${codes.length - 12} more` : codes.join(', '));
  return (
    <Alert severity={result.applied ? 'success' : 'info'} aria-label="Import check">
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {result.applied ? `Version ${result.versionLabel} imported: ${result.rows} requirements.`
          : `Checked: ${result.rows} requirements. Nothing has been imported yet.`}
      </Typography>
      <Typography variant="body2">Added {result.added.length}{result.added.length ? ` (${list(result.added)})` : ''}</Typography>
      <Typography variant="body2">Changed {result.changed.length}{result.changed.length ? ` (${list(result.changed)})` : ''}</Typography>
      <Typography variant="body2">
        Retired {result.retired.length}{result.retired.length ? ` (${list(result.retired)}): kept for history, no longer assessed` : ''}
      </Typography>
      <Typography variant="body2">Unchanged {result.unchanged}</Typography>
    </Alert>
  );
}

/**
 * Phase 3 (5): platform import of a framework's requirements (CSV or JSON) as a dated version. "Check file" runs the
 * server's dry run (every row validated, added / changed / retired listed); "Import version" applies it.
 */
export default function FrameworkContentTab() {
  const { data: frameworks = [] } = useFrameworksQuery();
  const [run, { isLoading }] = useImportFrameworkContentMutation();
  const [result, setResult] = useState(null);
  const [checked, setChecked] = useState(null); // the inputs the last successful dry run used
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, reset } = useForm({ defaultValues: emptyContentImport, resolver: zodResolver(contentImportSchema) });
  const values = useWatch({ control });
  const code = values.code;
  const sameAsChecked = checked && Object.keys(emptyContentImport).every((k) => checked[k] === values[k]);

  const submit = (dryRun) => handleSubmit(async (v) => {
    setFormError('');
    try {
      const res = await run(toContentImportBody(v, dryRun)).unwrap();
      setResult(res);
      setChecked(dryRun && res.errors.length === 0 ? v : null);
      if (!dryRun && res.applied) reset({ ...emptyContentImport, code: v.code });
    } catch (err) {
      setResult(null);
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyContentImport) }));
    }
  });

  const template = () => {
    const url = URL.createObjectURL(new Blob([`${CONTENT_HEADER}\n`], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'framework-content-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap', mb: 2 }}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>Import framework requirements</Typography>
              <Typography variant="body2" color="text.secondary">
                Every import is a new version with its effective dates. Requirements missing from a new version are retired, not deleted.
                Content stays &quot;pending SME verification&quot; until you record who verified it.
              </Typography>
            </Box>
            <Button size="small" startIcon={<Download />} onClick={template} sx={{ alignSelf: 'flex-start' }}>CSV template</Button>
          </Box>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <form onSubmit={submit(true)} noValidate>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <FormSelect control={control} name="code" label="Framework"
                  options={frameworks.map((f) => ({ value: f.code, label: `${f.code} · ${f.name}` }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller name="file" control={control} render={({ field, fieldState }) => (
                  <Box>
                    <Button component="label" variant="outlined" startIcon={<UploadFile />}>
                      {field.value ? field.value.name : 'Choose CSV or JSON file'}
                      <input hidden type="file" accept=".csv,.json" aria-label="Content file"
                        onChange={(e) => field.onChange(e.target.files?.[0] ?? null)} />
                    </Button>
                    {fieldState.error && <FormHelperText error>{fieldState.error.message}</FormHelperText>}
                  </Box>
                )} />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="versionLabel" label="Version label" helperText="e.g. 2026-09" /></Grid>
              <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="effectiveFrom" label="Effective from" /></Grid>
              <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="effectiveTo" label="Effective to (optional)" /></Grid>
              <Grid size={12}>
                <FormTextField control={control} name="source" label="Source instrument"
                  helperText="Title, issuer and date of the document this content comes from" />
              </Grid>
              <Grid size={{ xs: 12, sm: 5 }}>
                <Controller name="smeVerified" control={control} render={({ field }) => (
                  <FormControlLabel label="Verified by an ESG subject-matter expert"
                    control={<Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
                )} />
              </Grid>
              {values.smeVerified && (
                <Grid size={{ xs: 12, sm: 7 }}><FormTextField control={control} name="verifiedBy" label="Verified by" /></Grid>
              )}
            </Grid>
            <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
              <Button type="submit" variant="outlined" disabled={isLoading}>Check file</Button>
              <Button variant="contained" disabled={isLoading || !sameAsChecked} onClick={submit(false)}>Import version</Button>
              {!sameAsChecked && <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>Check the file first</Typography>}
            </Box>
          </form>
          {result && <Box sx={{ mt: 2 }}><Outcome result={result} /></Box>}
        </CardContent>
      </Card>
      <Versions code={code} />
    </>
  );
}
