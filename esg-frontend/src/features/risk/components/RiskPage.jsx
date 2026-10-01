import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function RiskPage() {
  return (
    <ModuleNotYetAvailable
      title="Risk & Materiality"
      subtitle="Double materiality, climate scenarios and ESG risk heatmaps"
      plannedIn="WP-32"
      capabilities={["Double materiality assessment", "ESG risk register and heatmap", "Climate scenario analysis"]} />
  );
}
