import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function SupplyChainPage() {
  return (
    <ModuleNotYetAvailable
      title="Supply Chain ESG"
      subtitle="Supplier scoring, questionnaires and Scope 3 estimation"
      plannedIn="WP-31"
      capabilities={["Supplier register and ESG questionnaires", "Supplier scoring (TRD \u00a78.6)", "Spend-based Scope 3 estimates"]} />
  );
}
