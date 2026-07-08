# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo layout

This `frontend/` directory is one module of a larger Gradle multi-project repo (root
`ceeds-backend`). Siblings at the repo root include `backend/` (Java 21 / Spring Boot) and
`api-specs/` (the shared OpenAPI spec `../api-specs/backend-api.yml` that this frontend's
types are generated from). Run all commands below from inside `frontend/`.

## Commands

- `pnpm install` — install dependencies
- `pnpm dev` — start Vite dev server (port 5173)
- `pnpm build` — regenerate API types, type-check, then build (`generate-openapi-schemas` →
  `type-check` → `build-only`); use this (not `build-only` alone) when the API spec may have
  changed
- `pnpm type-check` — `vue-tsc --build --force`
- `pnpm lint` — ESLint (oxlint + vue + typescript rules) with `--fix`
- `pnpm format` — Prettier over `src/`
- `pnpm generate-openapi-schemas` — regenerate `src/schema.d.ts` from `../api-specs/backend-api.yml`
  (config in `redocly.yaml`); run this after the OpenAPI spec changes
- No test runner is configured yet (Vitest ESLint config exists for `src/**/__tests__/*` but no
  tests/dependency are present).

Root-level Gradle also wires this up: `./gradlew :frontend:build` runs `pnpm run build` via the
Node/pnpm Gradle plugin.

## Architecture

- **API layer (`src/api.ts`)**: all backend calls go through here using `openapi-fetch`, typed
  against the generated `src/schema.d.ts`. Each function wraps a single endpoint and returns
  `{ data?, error?, response }`. Never hand-edit `schema.d.ts` — it's regenerated from the OpenAPI
  spec. `BASE_URL` resolves to `THYMELEAF_PUBLIC_URL` (injected server-side, see below) or falls
  back to `VITE_BASE_URL` from `.env` for local dev.
- **State (`src/stores/*.ts`)**: no Pinia — state is plain module-level `ref()`s exported directly
  (e.g. `referenceDataObjects`, `referenceDataObject` in `stores/referenceDataObject.ts`,
  `userRole` in `stores/userInfo.ts`). Views call the paired `update*()` function to refetch and
  mutate the ref; components import and read the ref directly rather than receiving it as a prop.
  `main.ts` eagerly loads `referenceDataObjects` before mounting.
- **Routing (`src/router/index.ts`)**: route guards read `userRole.value` directly (e.g. the
  create-object route redirects to `dashboard` unless `userRole === 'ceedsEntity'`). Role-gating
  in views (tabs, buttons) mirrors this same `userRole` check rather than a central permissions
  module.
- **Server-injected config (`index.html`, `env.d.ts`)**: the app is served from a Spring Boot
  Thymeleaf template. `index.html` has Thymeleaf comment-blocks injecting
  `THYMELEAF_PUBLIC_URL` / `THYMELEAF_KEYCLOAK_*` globals at render time (declared in `env.d.ts`);
  these are `undefined` in plain local dev, where `.env`'s `VITE_BASE_URL` is used instead.
  Keycloak auth wiring is present but currently commented out in `src/api.ts`.
- **Styling**: no component library — `src/assets/main.css` defines the design system as CSS
  custom properties (spacing scale, color palette, shadows, radii, tint/text color pairs via
  `color-mix()`). Components use scoped `<style>` blocks that consume these variables (e.g.
  `var(--spacing-lg)`, `var(--lavender)`); keep new UI consistent with this token set rather than
  hardcoding values.
- **Domain model**: a `ReferenceDataObject` has one or more `versions`, each with a
  `publishState` (`DRAFT`/`PUBLISHED`) and a list of `fields`. Non-`ceedsEntity` roles only ever
  see `PUBLISHED` versions/objects; `ceedsEntity` can create objects, start new draft versions,
  add fields (`FieldForm.vue`), and publish.

## Conventions

- No semicolons, single quotes, 100-char print width (Prettier, `.prettierrc.json`); formatting
  is auto-applied on save in VS Code and via `pnpm lint`/`pnpm format`.
- `@/` resolves to `src/` (see `vite.config.ts`).
- SVGs are imported as Vue components via `vite-svg-loader`, colorized with `currentColor`
  (SVGO `convertColors` config in `vite.config.ts`) — style icon color via CSS `color`, not by
  editing the SVG.
- Commit messages must start with an issue reference (`#123`, `GH-123`, `gh-123`) or `NOISSUE`
  (enforced by CI on non-`main` branches, `.github/workflows/commit-check.yml` at the repo root).
