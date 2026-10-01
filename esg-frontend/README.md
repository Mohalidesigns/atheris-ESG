# esg-frontend

React 19 + Vite + MUI frontend for Atheris ESG. See the [root README](../README.md) for how to run the whole stack.

| Script | What it does |
|---|---|
| `npm run dev` | Vite dev server on 5173; `/api` is proxied to the backend (`VITE_BACKEND_URL`, default `http://localhost:8080`) |
| `npm run lint` | ESLint |
| `npm test` | Vitest + React Testing Library (D7) |
| `npm run build` | Production build |
| `npm run format` / `format:check` | Prettier (not yet enforced in CI) |
| `npm run check:no-mocks` | No-mock gate report (`../scripts/check-no-mocks.mjs`) |
