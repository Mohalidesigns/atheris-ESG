import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function AnalyticsPage() {
  return (
    <ModuleNotYetAvailable
      title="Analytics"
      subtitle="Benchmarking, anomaly detection and SDG alignment"
      plannedIn="WP-35 (rules) / WP-50 (AI)"
      note="Earlier versions of this page showed predictions and anomalies that were not computed from any data; they have been removed (CLAUDE.md rule 10)."
      capabilities={["ESG maturity assessment and peer benchmarking", "Rules- and statistics-based anomaly detection on submitted data", "SDG alignment from mapped metrics", "AI assistance (Ollama + IBM Granite) only in Phase 5, grounded in your own data"]} />
  );
}
