import ModuleNotYetAvailable from '../../../components/states/ModuleNotYetAvailable';

export default function GovernancePage() {
  return (
    <ModuleNotYetAvailable
      title="Governance & Ethics"
      subtitle="Board composition, policies and whistleblowing, CAMA 2020 aligned"
      plannedIn="WP-34"
      capabilities={["Board members, independence and diversity", "Policy register with review dates", "Anonymous whistleblower channel with case management"]} />
  );
}
