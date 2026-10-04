import { useState } from 'react';
import {
  Alert, Card, CardContent, Chip, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography, Button,
} from '@mui/material';
import Can from '../../../components/rbac/Can';
import { EmptyState, ErrorState, LoadingState } from '../../../components/states/States';
import { roleLabel } from '../../../services/labels';
import { StepDialog } from '../../social/components/shared';
import { useCertificatesQuery, useRevokeCertificateMutation, useTeamQuery } from '../trainingApi';
import { CERT_STATUS, GAP_STATE, fmt } from '../schemas';
import { tr } from '../../../i18n';

/** Team progress (TRD §8.12 CompetencyTracker): per person, required courses from role paths, passes, overdue work and gaps. */
export function TeamProgress() {
  const { data, isLoading, isError, error, refetch } = useTeamQuery();
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Card>
      {data.length === 0 ? <CardContent><EmptyState title={tr("Nobody to show")} /></CardContent> : (
        <TableContainer>
          <Table size="small" aria-label={tr("Team progress")}>
            <TableHead><TableRow><TableCell>{tr("Person")}</TableCell><TableCell>{tr("Roles")}</TableCell><TableCell align="right">{tr("Required")}</TableCell><TableCell align="right">{tr("Passed")}</TableCell>
              <TableCell align="right">{tr("Overdue")}</TableCell><TableCell align="right">{tr("Hours this year")}</TableCell><TableCell>{tr("Gaps")}</TableCell></TableRow></TableHead>
            <TableBody>
              {data.map((r) => (
                <TableRow key={r.userId}>
                  <TableCell sx={{ fontWeight: 600 }}>{r.name}</TableCell>
                  <TableCell>{r.roles.map(roleLabel).join(', ')}</TableCell>
                  <TableCell align="right">{r.required}</TableCell>
                  <TableCell align="right">{r.passed}</TableCell>
                  <TableCell align="right">{r.overdue ? <Chip size="small" color="error" label={r.overdue} /> : 0}</TableCell>
                  <TableCell align="right">{fmt(r.hoursThisYear)}</TableCell>
                  <TableCell>{r.gaps.length === 0 ? <Chip size="small" color="success" label={tr("None")} />
                    : r.gaps.map((g) => <Typography key={g.courseId} variant="caption" display="block">{g.courseTitle}: {tr(GAP_STATE[g.state])}</Typography>)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Card>
  );
}

/** Certificates issued (D37 (5)); revocation needs a reason and shows on the public verification page. */
export function Certificates() {
  const { data, isLoading, isError, error, refetch } = useCertificatesQuery();
  const [revoke] = useRevokeCertificateMutation();
  const [revoking, setRevoking] = useState(null);
  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <>
      <Card>
        {data.length === 0 ? <CardContent><EmptyState title={tr("No certificates yet")}>{tr("They are issued when someone passes a course.")}</EmptyState></CardContent> : (
          <TableContainer>
            <Table size="small" aria-label={tr("Certificates")}>
              <TableHead><TableRow><TableCell>{tr("Code")}</TableCell><TableCell>{tr("Holder")}</TableCell><TableCell>{tr("Course")}</TableCell><TableCell>{tr("Issued")}</TableCell><TableCell>{tr("Expires")}</TableCell>
                <TableCell>{tr("Status")}</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {data.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{c.code}</TableCell>
                    <TableCell>{c.holderName}</TableCell>
                    <TableCell>{c.courseTitle}</TableCell>
                    <TableCell>{c.issuedOn}</TableCell>
                    <TableCell>{c.expiresOn ?? '—'}</TableCell>
                    <TableCell><Chip size="small" color={CERT_STATUS[c.status].color} label={tr(CERT_STATUS[c.status].label)} />
                      {c.revokeReason && <Typography variant="caption" display="block" color="text.secondary">{c.revokeReason}</Typography>}</TableCell>
                    <TableCell align="right">
                      {c.status !== 'revoked' && <Can permission="training:configure"><Button size="small" color="error" aria-label={`Revoke ${c.code}`} onClick={() => setRevoking(c)}>{tr("Revoke")}</Button></Can>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {revoking && (
        <StepDialog title={`Revoke certificate ${revoking.code}`} confirmLabel={tr("Revoke")} fields={[{ name: 'note', label: 'Reason', required: true, max: 1000 }]}
          onCancel={() => setRevoking(null)} onConfirm={async (v) => { await revoke({ id: revoking.id, note: v.note }).unwrap(); setRevoking(null); }}>
          <Alert severity="warning">{tr("Anyone checking the code will see that it has been revoked.")}</Alert>
        </StepDialog>
      )}
    </>
  );
}
