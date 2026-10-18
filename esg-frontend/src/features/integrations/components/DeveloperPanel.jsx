import { useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Grid, Typography } from '@mui/material';
import { Download } from '@mui/icons-material';
import Can from '../../../components/rbac/Can';
import { problemText, saveBlob } from '../../../services/download';
import { useImportTemplateMutation } from '../../dataHub/dataHubApi';
import { useApiDescriptionMutation } from '../integrationsApi';
import { tr } from '../../../i18n';

// no-mock-gate: config — the source templates offered (ImportService.SOURCE_TEMPLATES)
const TEMPLATES = [
  { kind: 'energy', label: 'Energy and fuel', from: 'ERP purchases, fleet cards, generator logs' },
  { kind: 'utilities', label: 'Utilities', from: 'Electricity and water bills or meters' },
  { kind: 'workforce', label: 'Workforce', from: 'HR system, payroll, learning system' },
  { kind: 'waste', label: 'Waste', from: 'Waste contractor manifests' },
  { kind: 'general', label: 'All columns, with examples', from: 'Any system' },
];

const Code = ({ children }) => (
  <Box component="pre" sx={{ p: 1.5, bgcolor: '#F7FAFC', border: '1px solid #E2E8F0', borderRadius: 1, fontSize: '0.75rem', overflowX: 'auto',
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', whiteSpace: 'pre' }}>{children}</Box>
);

/** WP-46 (D44): file templates per source system, and the developer guide (API keys, envelope, errors, webhook signatures). */
export function TemplatesPanel() {
  const [getTemplate] = useImportTemplateMutation();
  const [failure, setFailure] = useState('');
  const download = async (kind) => {
    setFailure('');
    try {
      const csv = await getTemplate(kind === 'general' ? undefined : kind).unwrap();
      saveBlob(new Blob([csv], { type: 'text/csv' }), kind === 'general' ? 'esg-data-import-template.csv' : `esg-import-${kind}.csv`);
    } catch (err) {
      setFailure(problemText(err));
    }
  };
  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {tr("Export from your system into a template, fill in the value column and upload it under Data Hub › Import. Each row becomes a draft data point checked by the same rules as the form.")}
      </Typography>
      {failure && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}
      <Grid container spacing={2}>
        {TEMPLATES.map((t) => (
          <Grid key={t.kind} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="subtitle2" component="h3" sx={{ fontWeight: 700 }}>{tr(t.label)}</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>{tr("From:")} {t.from}</Typography>
                <Can permission="data_points:create" fallback={<Typography variant="caption">{tr("Needs permission to enter data points")}</Typography>}>
                  <Button size="small" startIcon={<Download />} onClick={() => download(t.kind)} aria-label={`Download ${t.label} template`}>
                    {tr("Download")}
                  </Button>
                </Can>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}

export default function DeveloperPanel() {
  const [getDescription] = useApiDescriptionMutation();
  const [failure, setFailure] = useState('');
  const base = `${window.location.origin}/api/v1`;
  const downloadSpec = async () => {
    setFailure('');
    try {
      saveBlob(new Blob([await getDescription().unwrap()], { type: 'application/json' }), 'atheris-esg-openapi.json');
    } catch (err) {
      setFailure(problemText(err));
    }
  };
  return (
    <Box sx={{ maxWidth: 900 }}>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="body2" sx={{ flex: 1 }}>
          {tr("Every screen in Atheris ESG uses the same REST API. The full description of every endpoint is an OpenAPI 3 file you can load into Postman, Insomnia or a code generator.")}
        </Typography>
        <Button variant="outlined" size="small" startIcon={<Download />} onClick={downloadSpec}>{tr("Download API description")}</Button>
      </Box>
      {failure && <Alert severity="error" sx={{ mb: 2 }}>{failure}</Alert>}

      <Typography variant="h6" component="h2">{tr("1. Authenticate")}</Typography>
      <Typography variant="body2" sx={{ mb: 1 }}>{tr("Create an API key under API keys and send it with every request:")}</Typography>
      <Code>{`curl ${base}/data/points?size=5 \\\n  -H "X-API-Key: atk_xxxxxxxx_…"`}</Code>

      <Typography variant="h6" component="h2" sx={{ mt: 2 }}>{tr("2. Send data")}</Typography>
      <Typography variant="body2" sx={{ mb: 1 }}>
        {tr("Readings arrive as drafts and go through the same checks as the form (metric catalogue, units, locked periods, organisations the key's creator may enter data for). Metric keys and organisation ids come from")} <code>GET /reference/metrics</code> {tr("and")} <code>GET /organisations</code>.
      </Typography>
      <Code>{`curl -X POST ${base}/data/points \\\n  -H "X-API-Key: atk_xxxxxxxx_…" -H "Content-Type: application/json" \\\n  -d '{"orgId":"<organisation id>","metricKey":"diesel_litres","numericValue":1250.5,\n       "reportingPeriodStart":"2026-09-01","reportingPeriodEnd":"2026-09-30"}'`}</Code>

      <Typography variant="h6" component="h2" sx={{ mt: 2 }}>{tr("3. Read the answer")}</Typography>
      <Typography variant="body2" sx={{ mb: 1 }}>
        {tr("Successful responses are")} <code>{'{ "success": true, "data": …, "message": … }'}</code>{tr(". Errors are RFC 7807 problem details with an")}
        <code> errorCode</code> {tr("and, for invalid fields,")} <code>errors</code> {tr("by field name — e.g. 400")} <code>ESG-400-VALIDATION</code>,
        401 <code>ESG-401-API-KEY</code> {tr("(unknown, revoked or expired key), 403 (outside the key's permissions), 409 (locked period, changed record).")}
      </Typography>

      <Typography variant="h6" component="h2" sx={{ mt: 2 }}>{tr("4. Check webhook signatures")}</Typography>
      <Typography variant="body2" sx={{ mb: 1 }}>
        {tr("Each delivery has")} <code>X-Atheris-Signature: t=&lt;unix time&gt;,v1=&lt;hex&gt;</code>{tr(", where v1 is HMAC-SHA256 of")}
        <code> t + "." + raw body</code> {tr("with the webhook's secret. Reject old timestamps (e.g. over 5 minutes) and use")}
        <code> X-Atheris-Delivery</code> {tr("to ignore repeats.")}
      </Typography>
      <Code>{`// Node.js
const crypto = require('crypto');
function verify(rawBody, header, secret) {
  const { t, v1 } = Object.fromEntries(header.split(',').map((p) => p.split('=')));
  const expected = crypto.createHmac('sha256', secret).update(\`\${t}.\${rawBody}\`).digest('hex');
  const fresh = Math.abs(Date.now() / 1000 - Number(t)) < 300;
  return fresh && crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(v1));
}`}</Code>
    </Box>
  );
}
