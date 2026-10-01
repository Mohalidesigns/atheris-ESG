import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function ReportingPage() {
  return (
    <ModuleNotYetAvailable
      title="Reporting & Disclosure"
      subtitle="Build, review and publish ESG reports"
      plannedIn="WP-23 / WP-30"
      capabilities={["Report builder per framework, filled from approved data points", "Review and approval workflow", "Export to PDF and (later) XBRL"]} />
  );
}
