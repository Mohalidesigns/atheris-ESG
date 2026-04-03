import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { CircularProgress, Box } from '@mui/material';
import MainLayout from '../components/layout/MainLayout';
import { useAuth } from '../features/auth/hooks/useAuth';

const LoginForm = lazy(() => import('../features/auth/components/LoginForm'));
const DashboardPage = lazy(() => import('../features/dashboard/components/DashboardPage'));
const DataHubPage = lazy(() => import('../features/dataHub/components/DataHubPage'));
const CarbonPage = lazy(() => import('../features/carbon/components/CarbonPage'));
const CompliancePage = lazy(() => import('../features/compliance/components/CompliancePage'));
const ReportingPage = lazy(() => import('../features/reporting/components/ReportingPage'));
const RiskPage = lazy(() => import('../features/risk/components/RiskPage'));
const SupplyChainPage = lazy(() => import('../features/supplyChain/components/SupplyChainPage'));
const SocialPage = lazy(() => import('../features/social/components/SocialPage'));
const GovernancePage = lazy(() => import('../features/governance/components/GovernancePage'));
const AnalyticsPage = lazy(() => import('../features/analytics/components/AnalyticsPage'));
const InvestorPage = lazy(() => import('../features/investor/components/InvestorPage'));
const CarbonMarketPage = lazy(() => import('../features/carbonMarket/components/CarbonMarketPage'));
const TrainingPage = lazy(() => import('../features/training/components/TrainingPage'));
const AdminPage = lazy(() => import('../features/admin/components/AdminPage'));

const Loading = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
    <CircularProgress sx={{ color: '#1A365D' }} />
  </Box>
);

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" />;
  return children;
}

export default function AppRoutes() {
  const { isAuthenticated } = useAuth();

  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={isAuthenticated ? <Navigate to="/dashboard" /> : <LoginForm />} />
        <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="/dashboard" />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="data-hub" element={<DataHubPage />} />
          <Route path="carbon" element={<CarbonPage />} />
          <Route path="compliance" element={<CompliancePage />} />
          <Route path="reporting" element={<ReportingPage />} />
          <Route path="risk" element={<RiskPage />} />
          <Route path="supply-chain" element={<SupplyChainPage />} />
          <Route path="social" element={<SocialPage />} />
          <Route path="governance" element={<GovernancePage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="investor" element={<InvestorPage />} />
          <Route path="carbon-market" element={<CarbonMarketPage />} />
          <Route path="training" element={<TrainingPage />} />
          <Route path="admin" element={<AdminPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" />} />
      </Routes>
    </Suspense>
  );
}
