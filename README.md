# TaskFlow — Enterprise Project & Workflow Management

TaskFlow is a full-stack project and workflow management application. It provides
secure organization-scoped authentication, project/team/user administration,
task and kanban workflows, sprints, bug tracking, QA test management,
notifications, downloadable analytics reports, and a Live Analytics dashboard.

## Repository layout

| Path | Contents |
|---|---|
| `taskflow-backend/` | Spring Boot 4 REST API, security, analytics, reporting and Flyway migrations |
| `taskflow-frontend/` | React 19 + Vite 6 single-page application |

The repository root also contains leftover scaffold files from the original
AI Studio template (`index.html`, `package.json`, `src/`, `tsconfig.json`,
`vite.config.ts`, `metadata.json`, `projectmanager.sql`). They are **not** used
by the TaskFlow application; the runnable application lives in the two
directories above. They have been left in place intentionally and are not part
of the build.

## Features

- JWT authentication with HttpOnly refresh-token cookie and single-flight refresh.
- Role-based access (ADMIN, PROJECT_MANAGER, DEVELOPER, QA_TESTER) and
  organization/project/team isolation enforced on the server.
- Organization, user and team administration; project lifecycle and membership.
- Task CRUD, assignment, status transitions and kanban board.
- Sprint planning, velocity and burndown.
- Bug tracking with severity/status workflows.
- QA test cases, suites, runs and executions.
- Notifications and a recent-activity feed backed by audit logging.
- Ten analytics report types exported as PDF and XLSX (`/api/v1/reports/*`).
- Live Analytics SSE endpoint with a REST snapshot fallback
  (`/api/v1/live/*`).

## Technology stack

**Backend:** Java 21, Spring Boot 4.1.2 (WebMVC, Security, Data JPA,
Validation, Flyway, Actuator), MySQL 8+/Aiven JDBC, H2 (tests only), JJWT,
Apache PDFBox, Apache POI, springdoc-openapi.

**Frontend:** React 19, Vite 6, React Router 7, Axios, Tailwind CSS 4,
Recharts, framer-motion, lucide-react.

## Prerequisites

- JDK 21
- Maven 3.9+ (no Maven wrapper is committed)
- Node.js 20+ and npm

## Backend setup

1. Copy `taskflow-backend/.env.example` to `taskflow-backend/.env` and fill in
   the values for your environment. The `.env` file is git-ignored; never commit
   real credentials.
2. Required environment variable **names** (no values):
   - Database: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`,
     `DB_SSL_MODE`
   - Security: `JWT_SECRET` (minimum 256 bits), `JWT_ACCESS_EXPIRATION_MS`,
     `JWT_REFRESH_EXPIRATION_MS`, `JWT_COOKIE_SECURE`, `JWT_COOKIE_SAME_SITE`
   - CORS: `CORS_ALLOWED_ORIGINS`
   - Bootstrap: `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`,
     `BOOTSTRAP_ADMIN_NAME`
   - Development seeds: `DEV_SEED_ENABLED`, `DEV_SEED_PASSWORD`,
     `DEV_DEMO_SEED_ENABLED`
   - Storage: `STORAGE_PROVIDER`, `STORAGE_LOCAL_DIR`,
     `STORAGE_MAX_FILE_SIZE_BYTES`
   - Reports: `REPORT_MAX_BUCKETS`, `REPORT_MAX_ROWS`, `REPORT_MAX_KPIS`,
     `REPORT_MAX_PDF_PAGES`
   - Rate limits: `RATE_LIMIT_LOGIN_PER_IP`,
     `RATE_LIMIT_LOGIN_FAILURES_PER_ACCOUNT`, `RATE_LIMIT_REFRESH_PER_IP`,
     `RATE_LIMIT_ACTIVATION_PER_IP`, `RATE_LIMIT_REGISTRATION_PER_IP`,
     `RATE_LIMIT_WINDOW_SECONDS` (in-process store; use a shared store for
     multi-instance deployments)
3. Run:
   ```
   cd taskflow-backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   The API listens on port 8080 by default. Flyway applies `V1`–`V7` on
   startup. On a fresh database, create the first administrator through
   `GET /api/v1/setup/status` and `POST /api/v1/setup/admin`, or configure the
   bootstrap admin variables.

## Frontend setup

1. Copy `taskflow-frontend/.env.example` to `taskflow-frontend/.env.local` and
   set `VITE_API_BASE_URL` (default `http://localhost:8080/api/v1`).
2. Run:
   ```
   cd taskflow-frontend
   npm install
   npm run dev
   ```
   The app is served at `http://localhost:5173`. `npm run build` produces the
   production bundle in `dist/`.

## Tests and verification

- Backend: `cd taskflow-backend && mvn clean test` (H2 in-memory test profile;
  Flyway disabled in tests, `ddl-auto: create-drop`). The current suite is
  420 tests with 0 failures/errors.
- Flyway V1–V7 plus Hibernate `ddl-auto=validate` was verified separately on
  H2 in MySQL mode. Production MySQL/Aiven compatibility has **not** been
  verified in this repository's automated tests.
- Frontend: no test runner or lint script is configured; verify with
  `npm run build`.

## Main backend modules

- `controller/` — REST endpoints, including report and live-analytics controllers.
- `service/impl/` — domain services, `AnalyticsServiceImpl`, `AnalyticsReportServiceImpl`
  and the live-analytics snapshot/coalescing/registry components.
- `repository/` — JPA and native aggregate queries, all organization/project scoped.
- `security/` — JWT filter, `CustomUserDetails`, `ProjectSecurityEvaluator`.
- `dto/analytics/`, `dto/live/`, `dto/report/` — analytics, live and report contracts.
- `src/main/resources/db/migration/` — Flyway migrations V1–V7.
- `event/` — internal, minimal domain-change events published after commit.

## Main frontend modules

- `features/dashboard/` — dashboard and Live Analytics section.
- `features/reports/` — report catalog, filters and PDF/XLSX download flow.
- `features/tasks/`, `sprints/`, `bugs/`, `qa/`, `projects/`, `teams/`, `users/` —
  domain pages.
- `services/` — API clients (`apiClient.js` owns auth headers and token refresh;
  `liveAnalyticsService.js` owns the authenticated SSE transport).
- `hooks/` — `useAuth`, `useLiveAnalytics`, `useDebounce`, `useToast`.
- `components/common/`, `components/layout/` — reusable design-system components.
- `routes/` — protected and role-gated routing.

## Security notes

- The access token is kept only in memory (`tokenManager`); persistence uses
  the HttpOnly refresh cookie.
- All report and live endpoints re-resolve authorization on every request or
  connection; client-supplied organization IDs or permission lists are never
  accepted.
- Live subscriptions are bounded, revalidated periodically, and closed on
  logout or loss of authorization.

No screenshots are bundled in the repository. Verification artifacts (logs and
screenshots) were kept outside the repository under the local temporary folder.
