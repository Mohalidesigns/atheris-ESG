import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function InvestorPage() {
  return (
    <ModuleNotYetAvailable
      title="Investor Portal"
      subtitle="ESG scorecards and DFI reporting for investors"
      plannedIn="WP-40"
      capabilities={["Investor-facing ESG scorecards from approved data", "DFI reporting templates", "Controlled sharing of reports"]} />
  );
}
