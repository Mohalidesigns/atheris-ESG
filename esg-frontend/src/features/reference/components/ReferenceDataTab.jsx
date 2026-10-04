import { useState } from 'react';
import {
  Box, Card, Chip, MenuItem, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, TextField,
  Typography,
} from '@mui/material';
import { ErrorState, LoadingState } from '../../../components/states/States';
import {
  useCountriesQuery, useCurrenciesQuery, useMetricsQuery, useScope3CategoriesQuery, useSdgsQuery, useSectorsQuery,
} from '../referenceApi';
import { tr } from '../../../i18n';

/** Generic read-only table for a catalogue. columns: [[field, header]] */
function Catalogue({ query, columns, label }) {
  const { data, isLoading, isError, error, refetch } = query;
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Card>
      <TableContainer>
        <Table size="small" aria-label={label}>
          <TableHead><TableRow>{columns.map(([f, h]) => <TableCell key={f}>{h}</TableCell>)}</TableRow></TableHead>
          <TableBody>
            {data.map((row) => (
              <TableRow key={row[columns[0][0]]}>
                {columns.map(([f]) => <TableCell key={f} sx={{ fontSize: '0.8rem' }}>{row[f] ?? '—'}</TableCell>)}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {data[0]?.source && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', p: 1.5 }}>{tr("Source:")} {data[0].source}</Typography>}
    </Card>
  );
}

function Metrics() {
  const [pillar, setPillar] = useState('');
  const { data, isLoading, isError, error, refetch } = useMetricsQuery({ pillar: pillar || undefined });
  return (
    <>
      <TextField select size="small" label={tr("Pillar")} value={pillar} onChange={(e) => setPillar(e.target.value)} sx={{ mb: 2, minWidth: 160 }}>
        <MenuItem value="">{tr("All")}</MenuItem>
        <MenuItem value="E">{tr("Environmental")}</MenuItem>
        <MenuItem value="S">{tr("Social")}</MenuItem>
        <MenuItem value="G">{tr("Governance")}</MenuItem>
      </TextField>
      {isLoading && <LoadingState />}
      {isError && <ErrorState error={error} onRetry={refetch} />}
      {data && (
        <Card>
          <TableContainer>
            <Table size="small" aria-label={tr("Metric catalogue")}>
              <TableHead><TableRow>
                <TableCell>{tr("Metric")}</TableCell><TableCell>{tr("Pillar")}</TableCell><TableCell>{tr("Unit")}</TableCell><TableCell>{tr("Type")}</TableCell><TableCell>{tr("Feeds")}</TableCell>
              </TableRow></TableHead>
              <TableBody>
                {data.map((m) => (
                  <TableRow key={m.metricKey}>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{m.name}</Typography>
                      <Typography variant="caption" sx={{ fontFamily: 'Roboto Mono, monospace' }} color="text.secondary">{m.metricKey}</Typography>
                    </TableCell>
                    <TableCell>{m.pillar} · {m.category}</TableCell>
                    <TableCell>{m.unit}</TableCell>
                    <TableCell>{m.dataType}</TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                        {m.requirements.map((r) => <Chip key={`${r.framework}-${r.code}`} size="small" variant="outlined" label={`${r.framework} ${r.code}`} title={tr(r.title)} />)}
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}
    </>
  );
}

// no-mock-gate: config — tab names
const SECTIONS = ['Metrics', 'Currencies', 'Countries', 'SDGs', 'Scope 3 categories', 'Sectors'];

/** Read-only reference data (WP-17). Values change only through reviewed repeatable migrations. */
export default function ReferenceDataTab() {
  const [section, setSection] = useState(SECTIONS[0]);
  const currencies = useCurrenciesQuery(undefined, { skip: section !== 'Currencies' });
  const countries = useCountriesQuery(undefined, { skip: section !== 'Countries' });
  const sdgs = useSdgsQuery(undefined, { skip: section !== 'SDGs' });
  const scope3 = useScope3CategoriesQuery(undefined, { skip: section !== 'Scope 3 categories' });
  const sectors = useSectorsQuery(undefined, { skip: section !== 'Sectors' });
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {tr("Platform reference data is read-only here; every value names its source and is pending ESG SME review. Emission factors are on the Carbon Accounting page; framework requirements on the Compliance page.")}
      </Typography>
      <Tabs value={section} onChange={(_, v) => setSection(v)} variant="scrollable" sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none' } }}>
        {SECTIONS.map((s) => <Tab key={s} value={s} label={s} />)}
      </Tabs>
      {section === 'Metrics' && <Metrics />}
      {section === 'Currencies' && <Catalogue label={tr("Currencies")} query={currencies} columns={[['code', 'Code'], ['name', 'Name'], ['symbol', 'Symbol'], ['minorUnit', 'Decimals']]} />}
      {section === 'Countries' && <Catalogue label={tr("Countries")} query={countries} columns={[['code', 'Code'], ['name', 'Name'], ['region', 'Region'], ['currency', 'Currency']]} />}
      {section === 'SDGs' && <Catalogue label={tr("Sustainable Development Goals")} query={sdgs} columns={[['goal', 'Goal'], ['title', 'Title']]} />}
      {section === 'Scope 3 categories' && <Catalogue label={tr("Scope 3 categories")} query={scope3} columns={[['category', 'Category'], ['name', 'Name'], ['direction', 'Direction']]} />}
      {section === 'Sectors' && <Catalogue label={tr("Sectors")} query={sectors} columns={[['code', 'Section'], ['name', 'Name'], ['classification', 'Classification']]} />}
    </>
  );
}
