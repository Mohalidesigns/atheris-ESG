# Field round-trip specs (WP-18, master plan §7.2)

One JSON file per form in `docs/esg-completion/FIELD_INVENTORY.csv`. Two generators read them:

- **API** — `esg-backend/src/test/java/com/esg/roundtrip/FieldRoundTripTest.java`: create with every field and read it back; change one field at a time and check only that field changed; each invalid value returns 400 naming the field; a read-only role gets 403; masked fields come back null; tenant B can't read or change tenant A's record.
- **UI reload** — `esg-frontend/src/test/roundTripReload.test.jsx`: opens the real edit form with the saved record and checks every input shows the saved value.

`FieldRoundTripTest.inventoryIsFullyCovered` fails if a `WORKS` field in the inventory has no value here, or if a form is neither specced nor listed in `covered-elsewhere.json` with existing hand-written tests.

## Spec keys

| Key | Meaning |
|---|---|
| `id`, `forms` | Spec name; inventory `form_name`s it covers |
| `actor`, `readOnlyRole` | Fixture role that fills the form; role that must get 403 |
| `create` / `read` / `update` | Endpoints. `read.list: true` = find the record in a list; `update.fields` = fields the PUT accepts; `update.versioned` = send `version` |
| `singleton` | One record per tenant (settings): saved with PUT, restored afterwards |
| `kind: "command"` | Not a record (login, MFA): only success + validation |
| `values`, `changes` | Valid value for every field; the value each field changes to. Dotted keys (`admin.email`) build nested JSON |
| `invalid` | `{field, value, rule, errorField?}` → 400 with `errors[errorField]`. `{"$repeat": ["x", 256]}` builds long strings |
| `writeOnly`, `derived`, `masked` | Sent but never returned; set by the server; nulled for a role |
| `isolation.status` | What tenant B gets (404, or 403 for platform data) |
| `inventoryAliases` | Inventory `dto_property` → spec key |
| `ui.fields` | Label (and `kind`: select / switch / checkboxes, `display` text) of each field in the edit form; `ui.notInEditForm` explains omissions |

Placeholders: `{u}` (unique per record), `{yyyy}` (random far-future year, for periods), `{ORG_HQ}`, `{ORG_SITE_1}`, `{ORG_SITE_2}` (fixture organisations).
