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
import { tr, formattingLocale } from '../../../i18n';

function Versions({ code }) {
  const { data = [] } = useContentVersionsQuery(code, { skip: !code });
  if (!code) return null;
  return (
    <Card sx={{ mt: 2 }}>
      <CardContent>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>{tr("Content versions")}</Typography>
        {data.length === 0 ? <Typography variant="body2" color="text.secondary">{tr("No content imported for this framework yet.")}</Typography> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Content versions")}>
              <TableHead><TableRow>
                <TableCell>{tr("Version")}</TableCell><TableCell>{tr("Effective")}</TableCell><TableCell>{tr("Status")}</TableCell><TableCell>{tr("Source")}</TableCell>
                <TableCell align="right">{tr("Rows")}</TableCell><TableCell>{tr("Changes")}</TableCell><TableCell>{tr("Imported")}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.map((v) => (
                  <TableRow key={v.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{v.versionLabel}</TableCell>
                    <TableCell>{v.effectiveFrom}{v.effectiveTo ? ` – ${v.effectiveTo}` : ''}</TableCell>
                    <TableCell>
                      <Chip size="small" color={v.smeVerified ? 'success' : 'default'}
                        label={v.smeVerified ? `SME verified (${v.verifiedBy})` : tr("Pending SME verification")} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 280 }}>{v.source}</TableCell>
                    <TableCell align="right">{v.rows}</TableCell>
                    <TableCell>+{v.added} · ~{v.changed} · −{v.retired}</TableCell>
                    <TableCell>{v.importedBy} · {new Date(v.importedAt).toLocaleDateString(formattingLocale())}</TableCell>
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
      <Box aria-label={tr("Import check")}>
        <Alert severity="error" sx={{ mb: 1 }}>{tr("The file has")} {result.errors.length} {tr("problem")}{result.errors.length === 1 ? '' : 's'}{tr("; nothing was imported.")}</Alert>
        <TableContainer>
          <Table size="small" aria-label={tr("File problems")}>
            <TableHead><TableRow><TableCell>{tr("Line")}</TableCell><TableCell>{tr("Column")}</TableCell><TableCell>{tr("Problem")}</TableCell></TableRow></TableHead>
            <TableBody>
              {result.errors.map((e, i) => (
                <TableRow key={`${e.line}-${i}`}><TableCell>{e.line || '—'}</TableCell><TableCell>{e.field ?? '—'}</TableCell><TableCell>{tr(e.message)}</TableCell></TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    );
  }
  const list = (codes) => (codes.length > 12 ? `${codes.slice(0, 12).join(', ')} and ${codes.length - 12} more` : codes.join(', '));
  return (
    <Alert severity={result.applied ? 'success' : 'info'} aria-label={tr("Import check")}>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {result.applied ? `Version ${result.versionLabel} imported: ${result.rows} requirements.`
          : `Checked: ${result.rows} requirements. Nothing has been imported yet.`}
      </Typography>
      <Typography variant="body2">{tr("Added")} {result.added.length}{result.added.length ? ` (${list(result.added)})` : ''}</Typography>
      <Typography variant="body2">{tr("Changed")} {result.changed.length}{result.changed.length ? ` (${list(result.changed)})` : ''}</Typography>
      <Typography variant="body2">
        {tr("Retired")} {result.retired.length}{result.retired.length ? ` (${list(result.retired)}): kept for history, no longer assessed` : ''}
      </Typography>
      <Typography variant="body2">{tr("Unchanged")} {result.unchanged}</Typography>
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
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{tr("Import framework requirements")}</Typography>
              <Typography variant="body2" color="text.secondary">
                {tr("Every import is a new version with its effective dates. Requirements missing from a new version are retired, not deleted. Content stays \"pending SME verification\" until you record who verified it.")}
              </Typography>
            </Box>
            <Button size="small" startIcon={<Download />} onClick={template} sx={{ alignSelf: 'flex-start' }}>{tr("CSV template")}</Button>
          </Box>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <form onSubmit={submit(true)} noValidate>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <FormSelect control={control} name="code" label={tr("Framework")}
                  options={frameworks.map((f) => ({ value: f.code, label: `${f.code} · ${f.name}` }))} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Controller name="file" control={control} render={({ field, fieldState }) => (
                  <Box>
                    <Button component="label" variant="outlined" startIcon={<UploadFile />}>
                      {field.value ? field.value.name : tr("Choose CSV or JSON file")}
                      <input hidden type="file" accept=".csv,.json" aria-label={tr("Content file")}
                        onChange={(e) => field.onChange(e.target.files?.[0] ?? null)} />
                    </Button>
                    {fieldState.error && <FormHelperText error>{tr(fieldState.error.message)}</FormHelperText>}
                  </Box>
                )} />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}><FormTextField control={control} name="versionLabel" label={tr("Version label")} helperText={tr("e.g. 2026-09")} /></Grid>
              <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="effectiveFrom" label={tr("Effective from")} /></Grid>
              <Grid size={{ xs: 6, sm: 4 }}><FormDateField control={control} name="effectiveTo" label={tr("Effective to (optional)")} /></Grid>
              <Grid size={12}>
                <FormTextField control={control} name="source" label={tr("Source instrument")}
                  helperText={tr("Title, issuer and date of the document this content comes from")} />
              </Grid>
              <Grid size={{ xs: 12, sm: 5 }}>
                <Controller name="smeVerified" control={control} render={({ field }) => (
                  <FormControlLabel label={tr("Verified by an ESG subject-matter expert")}
                    control={<Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} />
                )} />
              </Grid>
              {values.smeVerified && (
                <Grid size={{ xs: 12, sm: 7 }}><FormTextField control={control} name="verifiedBy" label={tr("Verified by")} /></Grid>
              )}
            </Grid>
            <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
              <Button type="submit" variant="outlined" disabled={isLoading}>{tr("Check file")}</Button>
              <Button variant="contained" disabled={isLoading || !sameAsChecked} onClick={submit(false)}>{tr("Import version")}</Button>
              {!sameAsChecked && <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>{tr("Check the file first")}</Typography>}
            </Box>
          </form>
          {result && <Box sx={{ mt: 2 }}><Outcome result={result} /></Box>}
        </CardContent>
      </Card>
      <Versions code={code} />
    </>
  );
}
