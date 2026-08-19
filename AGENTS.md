# Repository Guidelines

## Project Structure & Module Organization

xLumen is a pnpm monorepo plus a Maven modular monolith. Backend: `backend/xlumen-server/` (parent POM, 7 modules, `sql/init/`, `config/.env.example`). Frontend: `frontend/xlumen-frontend-blog/` (blog/content, `:5173`) and `frontend/xlumen-frontend-admin/` (admin config, `:5174`). Unit tests are in `src/**/__tests__/`; E2E tests are in each app's `e2e/`. `docs/` is canonical; AI contributors must read `docs/ai/STATUS.md` and `docs/ai/CHANGELOG.md`.

## Build, Test, and Development Commands

From `backend/xlumen-server/`:

```powershell
mvn -T 1C clean verify
mvn -pl xlumen-boot -am package -DskipTests
java -jar xlumen-boot/target/xlumen-boot-*.jar
```

From the repository root:

```powershell
pnpm install
pnpm dev:blog
pnpm dev:admin
pnpm lint
pnpm stylelint
pnpm typecheck
pnpm test
pnpm build
pnpm test:e2e
```

## Coding Style & Naming Conventions

`.editorconfig` uses 2-space indentation for frontend files, 4 spaces for Java, XML, and SQL, and LF endings with UTF-8. Frontend code uses TypeScript strict mode, ESLint 9 flat config, Stylelint, and Prettier, with module code scoped under `src/modules/`.

Backend modules use flat MVC packages: `controller`, `service`, `mapper`, `entity`, `dto`, and `vo`. Name classes by resource noun, use `XxxApi` and `XxxApiImpl` for cross-module access, and use Lombok classes for DTOs and VOs. Environment variables use the `XLUMEN_*` prefix. SQL tables use module-specific prefixes and `uk_` unique keys.

## Testing Guidelines

Backend tests use JUnit 5, AssertJ, Mockito, and Spring Boot Test. Cover service rules, state transitions, permissions, and idempotency. Use WireMock or fixed responses for external services; never call paid AI models in tests.

Frontend tests use Vitest and Vue Test Utils, named `*.spec.ts` under `__tests__/`. Playwright E2E tests should exercise user-visible behavior, not CSS classes or internal component state.

## Commit & Pull Request Guidelines

Use Conventional Commits, for example `feat(content): add article review flow` or `fix: make comment assertions idempotent`. Keep PRs small and focused. Describe the change, reference `F-xxxx` or `Mxx`, list passed verification, include screenshots for UI or E2E changes, and update affected `docs/` files in the same PR.

## Security & Configuration

Copy `config/.env.example` to `config/.env`. Never commit secrets or write them into logs, errors, API responses, or test snapshots. Use `xlumen_test` only for integration tests that require real middleware.

## Documentation & Agent Workflow

This repository is docs-first. Before coding, read `README.md`, `docs/ai/STATUS.md`, and the relevant `BACKEND.md` or `FRONTEND.md` sections. Implement one verifiable slice at a time, keep docs synchronized with code, and do not invent dependencies, tables, or APIs outside the documented contracts.
