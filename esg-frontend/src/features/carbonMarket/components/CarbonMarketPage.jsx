import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function CarbonMarketPage() {
  return (
    <ModuleNotYetAvailable
      title="Carbon Market"
      subtitle="Carbon credit portfolio and African offset projects"
      plannedIn="WP-41"
      capabilities={["Credit portfolio and retirements", "Offset project registry", "ACMI and registry integration through adapters (sandbox adapters are labelled)"]} />
  );
}
