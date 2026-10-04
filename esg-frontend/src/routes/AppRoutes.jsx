import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { CircularProgress, Box, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import { useRestoreSessionMutation } from '../features/auth/authApi';
import { allNavItems, firstAllowedPath, moduleEnabled } from './navigation';
import { tr } from '../i18n';

const LoginForm = lazy(() => import('../features/auth/components/LoginForm'));
// WP-53: the app shell loads after sign-in, so the sign-in page downloads only what it needs (3G target).
const MainLayout = lazy(() => import('../components/layout/MainLayout'));
// WP-34: the public whistleblower portal (no session) and everyone's policy acknowledgements.
const WhistleblowerPortal = lazy(() => import('../features/governance/components/WhistleblowerPortal'));
const MyPoliciesPage = lazy(() => import('../features/governance/components/MyPoliciesPage'));
// WP-31: a supplier answers a questionnaire through its link (no session).
const SupplierQuestionnairePortal = lazy(() => import('../features/supplyChain/components/SupplierQuestionnairePortal'));
// WP-42: anyone checks a training certificate by its code or QR code (no session).
const VerifyCertificatePage = lazy(() => import('../features/training/components/VerifyCertificatePage'));
// WP-43: opened without a connection by someone who signed in on this device before — offline capture only.
const OfflineCapturePage = lazy(() => import('../features/offline/components/OfflineCapturePage'));
const pages = {
  '/dashboard': lazy(() => import('../features/dashboard/components/DashboardPage')),
  '/data-hub': lazy(() => import('../features/dataHub/components/DataHubPage')),
  '/carbon': lazy(() => import('../features/carbon/components/CarbonPage')),
  '/compliance': lazy(() => import('../features/compliance/components/CompliancePage')),
  '/reporting': lazy(() => import('../features/reporting/components/ReportingPage')),
  '/risk': lazy(() => import('../features/risk/components/RiskPage')),
  '/supply-chain': lazy(() => import('../features/supplyChain/components/SupplyChainPage')),
  '/social': lazy(() => import('../features/social/components/SocialPage')),
  '/governance': lazy(() => import('../features/governance/components/GovernancePage')),
  '/analytics': lazy(() => import('../features/analytics/components/AnalyticsPage')),
  '/investor': lazy(() => import('../features/investor/components/InvestorPage')),
  '/carbon-market': lazy(() => import('../features/carbonMarket/components/CarbonMarketPage')),
  '/training': lazy(() => import('../features/training/components/TrainingPage')),
  '/admin': lazy(() => import('../features/admin/components/AdminPage')),
};

const Loading = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
    <CircularProgress sx={{ color: '#1A365D' }} />
  </Box>
);

export function Forbidden() {
  return (
    <Box sx={{ textAlign: 'center', py: 10 }} role="alert">
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>{tr("You don't have access to this page")}</Typography>
      <Typography color="text.secondary">{tr("Ask your administrator if you need it.")}</Typography>
    </Box>
  );
}

/** A module the tenant's subscription doesn't include (WP-19). */
export function NotInPlan() {
  return (
    <Box sx={{ textAlign: 'center', py: 10 }} role="alert">
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>{tr("This module isn't part of your subscription")}</Typography>
      <Typography color="text.secondary">{tr("Your administrator can see your plan under Administration → Subscription.")}</Typography>
    </Box>
  );
}

/** Requires a session; with `module`, a subscription that includes it; with `anyOf`, one of those permissions (TRD §7.2.2). */
export function ProtectedRoute({ anyOf, module, children }) {
  const { status, user } = useSelector((s) => s.auth);
  const location = useLocation();
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (module && !moduleEnabled(user?.modules, module)) return <NotInPlan />;
  const permissions = user?.permissions ?? [];
  if (anyOf && !anyOf.some((p) => permissions.includes(p))) return <Forbidden />;
  return children;
}

function Landing() {
  const user = useSelector((s) => s.auth.user);
  const path = firstAllowedPath(user?.permissions ?? [], user?.modules);
  return path ? <Navigate to={path} replace /> : <Forbidden />;
}

export default function AppRoutes() {
  const status = useSelector((s) => s.auth.status);
  const [restoreSession] = useRestoreSessionMutation();

  useEffect(() => {
    if (status === 'unknown') restoreSession();
  }, [status, restoreSession]);

  if (status === 'unknown') return <Loading />;
  if (status === 'offline') return <Suspense fallback={<Loading />}><OfflineCapturePage /></Suspense>;

  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={status === 'authenticated' ? <Landing /> : <LoginForm />} />
        <Route path="/whistleblower/:tenant" element={<WhistleblowerPortal />} />
        <Route path="/supplier-questionnaire/:token" element={<SupplierQuestionnairePortal />} />
        <Route path="/verify-certificate" element={<VerifyCertificatePage />} />
        <Route path="/verify-certificate/:code" element={<VerifyCertificatePage />} />
        <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
          <Route index element={<Landing />} />
          <Route path="my-policies" element={<MyPoliciesPage />} />
          {allNavItems.map(({ path, anyOf, module }) => {
            const Page = pages[path];
            return (
              <Route key={path} path={path.slice(1)}
                element={<ProtectedRoute anyOf={anyOf} module={module}><Page /></ProtectedRoute>} />
            );
          })}
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
