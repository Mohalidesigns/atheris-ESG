# M2 Carbon Accounting (`/carbon`)

BRD §5.2.2, §6.3; TRD §4.4, §6.4.2, §7.4, §8.2. Built in WP-17 (factor library) and WP-21. Method choices: D22.

## What it does

A year selector at the top scopes the Overview, Emission records, Gas flaring and Financed tabs.

| Tab | Permission | Purpose |
|---|---|---|
| Overview | `carbon_data:read` | Scope 1, Scope 2 (location- and market-based), Scope 3 and totals; monthly, by source, by organisation, Scope 3 by category; intensity |
| Emission records | `carbon_data:read` | Every record in the user's org scope with its factor (value, unit, version, source, level) and source (Calculator, Data Hub, Flaring); **Calculate emissions** (`carbon_data:create`); verify (`carbon_data:approve`) |
| Gas flaring | `carbon_data:read` | Flaring records by facility with CO2, CH4 slip and CO2e; satellite check (Sandbox) |
| Reduction targets | `carbon_data:read` | Targets with pathway chart, progress, status and SBTi findings |
| RECs & PPAs | `carbon_data:read` | Energy attribute instruments with the MWh matched and tonnes avoided |
| Financed (PCAF) | `carbon_data:read` | Financed positions and totals by asset class |
| Emission factors | `carbon_data:read` | Platform and tenant factors with source, derivation, status and per-gas values; custom factors (`emission_factors:manage`); recalculation (`carbon_data:update`) |

The **Emission Calculator** (TRD §7.4) walks scope (and Scope 3 category, activity- or spend-based) → activity,
organisation and period → amount → result. The result comes from `POST /carbon/emissions/preview`, which runs the
same code as saving, so what is shown is what is stored.

## Where records come from

| Source | How | Editable here |
|---|---|---|
| Calculator | Saved from the wizard | Yes (recalculated on save) |
| Data Hub | Approving `electricity_kwh`, `diesel_litres`, `petrol_litres`, `lpg_kg` or `natural_gas_m3` (`reference.metric_activities`); reopening the point deletes the record | No: change the data point |
| Flaring | One Scope 1 record per flaring record | No: edit the flaring record |

## Formulas (golden-tested)

- CO2e (t) = activity × (EF_CO2 + EF_CH4 × GWP_CH4 + EF_N2O × GWP_N2O) ÷ 1000; CO2e-only factors are applied as
  published. GWP set per tenant (AR6 default, AR5) — Admin → Settings. `CarbonCalculatorsTest`, `CarbonApiTest`.
- Factor choice: the chosen factor, else tenant custom → country → region → global, valid at the period end.
- Spend-based: amount (converted to the factor's currency with the tenant's FX rate) × kgCO2e per currency unit.
- Market-based Scope 2 = location-based − avoided; avoided = min(instrument MWh, matched kWh) × (grid factor −
  instrument factor).
- Flaring (API Compendium): n = Mcf × 1000 ÷ 379.3; CO2 = n × (η Σ yᵢCᵢ + y_CO2) × 44.01 lb; CH4 = n × y_CH4 × (1 − η)
  × 16.043 lb; η = 0.98 by default. Golden: 1,000 Mcf of the test gas → 69.828118 t (AR6).
- Targets: linear (equal tonnes) or contraction (equal %); progress = (base − actual) ÷ (base − target).
- PCAF: attribution = outstanding ÷ denominator (≤ 1); financed = attribution × counterparty emissions.

## API (`/api/v1/carbon`)

| Method | Path | Permission |
|---|---|---|
| GET | `/emissions`, `/emissions/{id}`, `/emissions/summary?year` | `carbon_data:read` |
| POST | `/emissions`, `/emissions/preview` | `carbon_data:create` |
| PUT / DELETE | `/emissions/{id}` | `carbon_data:update` / `carbon_data:delete` |
| PUT | `/emissions/{id}/verify` | `carbon_data:approve` |
| POST | `/calculate` (recalculation job for `{from, to}`) | `carbon_data:update` |
| GET | `/gwp` | `carbon_data:read` |
| CRUD | `/flaring`, `/instruments`, `/targets`, `/financed` | `carbon_data:*` |
| POST | `/flaring/{id}/satellite-check` | `carbon_data:update` |
| GET | `/financed/summary?year` | `carbon_data:read` |
| GET / POST / PUT | `/factors` | `carbon_data:read` / `emission_factors:manage` |

## Tests

- Unit goldens: `CarbonCalculatorsTest`. API: `CarbonApiTest` (journeys 5–9, provenance, recalculation, Data Hub
  link and lineage, market-based Scope 2, PCAF, dashboard and intensity).
- Field round-trip: 8 specs in `testing/roundtrip/` (+ the GWP setting) through `FieldRoundTripTest` and
  `roundTripReload.test.jsx`.
- UI: `CarbonPage.test.jsx`. E2E: `cypress/e2e/journey-05` … `journey-09`.

## Not built yet

EEIO spend factors and the hybrid Scope 3 method; residual-mix factors and detection of double-claimed instruments;
a real satellite provider (Sandbox only); a lineage viewer. See STATUS WP-21 "Open" and D22.
