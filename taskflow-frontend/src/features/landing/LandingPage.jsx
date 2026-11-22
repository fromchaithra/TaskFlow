import { Link } from 'react-router-dom';
import {
  ArrowRight,
  BarChart3,
  Bug,
  Building2,
  Check,
  CheckCircle2,
  Columns3,
  FolderKanban,
  IterationCw,
  Layers,
  Lock,
  ShieldCheck,
  UsersRound,
  Workflow,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { BrandMark } from '../../components/common/BrandMark';

const CAPABILITIES = [
  {
    icon: FolderKanban,
    title: 'Project portfolios',
    description:
      'Define delivery scope, lifecycle status, target dates, and accountable managers for every initiative.',
  },
  {
    icon: Columns3,
    title: 'Kanban workflow',
    description:
      'Move work through controlled status transitions with a board that mirrors your delivery process.',
  },
  {
    icon: IterationCw,
    title: 'Sprint planning',
    description:
      'Build backlogs, start cycles, track completion, and close iterations with a clear delivery record.',
  },
  {
    icon: CheckCircle2,
    title: 'Task execution',
    description:
      'Assign owners, set priorities and estimates, and keep a full history of every engineering change.',
  },
  {
    icon: Bug,
    title: 'QA verification',
    description:
      'Report defects, triage severity, coordinate fixes, and verify resolution before release.',
  },
  {
    icon: UsersRound,
    title: 'Teams & governance',
    description:
      'Organize squads, appoint team leads, and manage roster membership with role-scoped controls.',
  },
];

const ROLES = [
  {
    role: 'Administrator',
    scope: 'Organization governance, employee provisioning, cross-project oversight.',
    tone: 'bg-brand-50 text-brand-700 border-brand-100',
  },
  {
    role: 'Project Manager',
    scope: 'Project delivery, sprint planning, member allocation, workflow execution.',
    tone: 'bg-sky-50 text-sky-700 border-sky-100',
  },
  {
    role: 'Developer',
    scope: 'Task implementation, Kanban progression, and defect resolution.',
    tone: 'bg-emerald-50 text-emerald-700 border-emerald-100',
  },
  {
    role: 'QA Tester',
    scope: 'Quality assurance, bug reporting, verification, and release readiness.',
    tone: 'bg-amber-50 text-amber-700 border-amber-100',
  },
];

const WORKFLOW = [
  {
    step: '01',
    title: 'Plan',
    description: 'Structure projects, scope sprints, and assign accountable owners.',
  },
  {
    step: '02',
    title: 'Execute',
    description: 'Progress tasks across the board with full activity history.',
  },
  {
    step: '03',
    title: 'Verify',
    description: 'Log defects, coordinate fixes, and confirm resolution with QA.',
  },
  {
    step: '04',
    title: 'Report',
    description: 'Review sprint outcomes and delivery health in one workspace.',
  },
];

const TRUST_POINTS = [
  {
    icon: ShieldCheck,
    title: 'Role-based access control',
    description: 'Every route, action, and record is scoped to the signed-in role.',
  },
  {
    icon: Lock,
    title: 'Stateless token security',
    description: 'Short-lived access tokens with rotating HttpOnly refresh sessions.',
  },
  {
    icon: Building2,
    title: 'Organization isolation',
    description: 'Workspace data stays inside the organization boundary.',
  },
];

const BOARD_COLUMNS = [
  {
    title: 'In progress',
    accent: 'bg-brand-500',
    tasks: [
      { key: 'TF-142', title: 'Sprint velocity reporting', tag: 'Backend' },
      { key: 'TF-137', title: 'Kanban transition rules', tag: 'Frontend' },
    ],
  },
  {
    title: 'In review',
    accent: 'bg-sky-500',
    tasks: [{ key: 'TF-129', title: 'Refresh token rotation', tag: 'Security' }],
  },
  {
    title: 'Verified',
    accent: 'bg-emerald-500',
    tasks: [
      { key: 'TF-121', title: 'Assignment notifications', tag: 'QA' },
      { key: 'TF-118', title: 'Team roster permissions', tag: 'Backend' },
    ],
  },
];

function SectionLabel({ children }) {
  return (
    <p className="inline-flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-brand-700">
      <span className="h-1.5 w-1.5 rounded-full bg-brand-500" aria-hidden="true" />
      {children}
    </p>
  );
}

function BoardPreview() {
  return (
    <div className="relative">
      <div
        aria-hidden="true"
        className="absolute -inset-4 rounded-[28px] bg-brand-100/50 blur-2xl"
      />
      <div className="relative rounded-2xl border border-line bg-surface shadow-overlay overflow-hidden">
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-line">
          <div className="min-w-0">
            <p className="text-[11px] font-mono uppercase tracking-wider text-ink-faint">
              Sprint 12 · Delivery board
            </p>
            <p className="text-sm font-semibold text-ink mt-1 truncate">
              Platform hardening
            </p>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 border border-emerald-100 px-2.5 py-1 text-[10px] font-semibold text-emerald-700 shrink-0">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" aria-hidden="true" />
            On track
          </span>
        </div>

        <div className="grid grid-cols-3 gap-3 p-4 bg-subtle/50">
          {BOARD_COLUMNS.map((column) => (
            <div key={column.title} className="min-w-0">
              <div className="flex items-center gap-1.5 mb-2 px-0.5">
                <span className={`h-1.5 w-1.5 rounded-full ${column.accent}`} aria-hidden="true" />
                <p className="text-[10px] font-semibold uppercase tracking-wider text-ink-faint truncate">
                  {column.title}
                </p>
              </div>
              <div className="space-y-2">
                {column.tasks.map((task) => (
                  <div
                    key={task.key}
                    className="rounded-lg border border-line bg-surface p-2.5 shadow-card"
                  >
                    <p className="text-[9px] font-mono text-ink-faint">{task.key}</p>
                    <p className="text-[10px] font-medium text-ink leading-snug mt-1 line-clamp-2">
                      {task.title}
                    </p>
                    <span className="mt-2 inline-flex rounded bg-subtle px-1.5 py-0.5 text-[9px] font-medium text-ink-muted">
                      {task.tag}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-between gap-3 px-5 py-3 border-t border-line bg-surface">
          <div className="flex items-center gap-2 text-[10px] text-ink-faint">
            <BarChart3 className="h-3.5 w-3.5 text-brand-600" aria-hidden="true" />
            <span>Sprint completion</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="h-1.5 w-24 rounded-full bg-subtle overflow-hidden">
              <div className="h-full w-[72%] rounded-full bg-brand-500" />
            </div>
            <span className="text-[10px] font-mono text-ink-muted">72%</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export function LandingPage() {
  const { isAuthenticated, isBootstrapping } = useAuth();
  const primaryTarget = !isBootstrapping && isAuthenticated ? '/dashboard' : '/login';
  const primaryLabel = !isBootstrapping && isAuthenticated ? 'Open workspace' : 'Sign in';

  return (
    <div className="min-h-screen bg-canvas text-ink">
      {/* Public header */}
      <header className="sticky top-0 z-40 bg-canvas/85 backdrop-blur-md border-b border-line">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
          <Link to="/" className="flex items-center gap-2.5 shrink-0" aria-label="TaskFlow home">
            <BrandMark size={30} />
            <span className="text-base font-semibold tracking-tight text-ink">TaskFlow</span>
          </Link>

          <nav aria-label="Product" className="hidden md:flex items-center gap-7">
            <a
              href="#platform"
              className="text-[13px] font-medium text-ink-muted hover:text-ink transition-colors"
            >
              Platform
            </a>
            <a
              href="#capabilities"
              className="text-[13px] font-medium text-ink-muted hover:text-ink transition-colors"
            >
              Capabilities
            </a>
            <a
              href="#workflow"
              className="text-[13px] font-medium text-ink-muted hover:text-ink transition-colors"
            >
              Workflow
            </a>
            <a
              href="#security"
              className="text-[13px] font-medium text-ink-muted hover:text-ink transition-colors"
            >
              Security
            </a>
          </nav>

          <div className="flex items-center gap-2 shrink-0">
            <Link
              to="/login"
              className="hidden sm:inline-flex items-center justify-center h-9 px-4 rounded-lg text-sm font-medium text-ink hover:bg-subtle transition-colors"
            >
              Log in
            </Link>
            <Link
              to={primaryTarget}
              className="inline-flex items-center justify-center h-9 px-4 rounded-lg bg-brand-solid text-white text-sm font-semibold shadow-sm hover:bg-brand-solid-hover transition-colors"
            >
              {primaryLabel}
            </Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden border-b border-line">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 tf-grid-pattern tf-grid-fade opacity-60"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-40 right-0 h-[420px] w-[420px] rounded-full bg-brand-100/50 blur-3xl"
        />

        <div className="relative mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-16 lg:py-24 grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center">
          <div>
            <SectionLabel>Enterprise delivery platform</SectionLabel>
            <h1 className="mt-5 text-4xl sm:text-5xl font-semibold tracking-tight leading-[1.1] text-ink">
              Ship engineering work
              <span className="block text-brand-600">with clarity.</span>
            </h1>
            <p className="mt-5 text-base sm:text-lg text-ink-muted leading-relaxed max-w-xl">
              TaskFlow brings projects, sprint cycles, Kanban execution, and QA verification into
              one role-governed workspace — so every team knows what is next and what is done.
            </p>

            <div className="mt-8 flex flex-col sm:flex-row gap-3">
              <Link
                to={primaryTarget}
                className="inline-flex items-center justify-center gap-2 h-11 px-6 rounded-lg bg-brand-solid text-white text-sm font-semibold shadow-sm hover:bg-brand-solid-hover transition-colors"
              >
                {isBootstrapping ? 'Sign in' : primaryLabel}
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
              <a
                href="#capabilities"
                className="inline-flex items-center justify-center gap-2 h-11 px-6 rounded-lg bg-surface border border-line-strong text-sm font-medium text-ink shadow-sm hover:bg-subtle transition-colors"
              >
                Explore capabilities
              </a>
            </div>

            <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-2 text-xs text-ink-muted">
              {['Role-based access', 'Sprint cycles', 'Kanban boards', 'QA verification'].map(
                (item) => (
                  <span key={item} className="inline-flex items-center gap-1.5">
                    <Check className="h-3.5 w-3.5 text-brand-600" aria-hidden="true" />
                    {item}
                  </span>
                )
              )}
            </div>
          </div>

          <BoardPreview />
        </div>
      </section>

      {/* Capabilities */}
      <section id="capabilities" className="scroll-mt-20 bg-subtle border-b border-line">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-16 lg:py-20">
          <div className="max-w-2xl">
            <SectionLabel>Capabilities</SectionLabel>
            <h2 className="mt-4 text-2xl sm:text-3xl font-semibold tracking-tight text-ink">
              Everything the delivery loop needs
            </h2>
            <p className="mt-3 text-sm sm:text-base text-ink-muted leading-relaxed">
              One connected system for planning, execution, and verification — without stitching
              together disconnected tools.
            </p>
          </div>

          <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {CAPABILITIES.map((capability) => {
              const Icon = capability.icon;
              return (
                <div
                  key={capability.title}
                  className="rounded-xl border border-line bg-surface p-5 shadow-card transition-shadow hover:shadow-card-hover"
                >
                  <div className="h-10 w-10 rounded-lg bg-brand-50 border border-brand-100 text-brand-600 flex items-center justify-center">
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </div>
                  <h3 className="mt-4 text-sm font-semibold text-ink">{capability.title}</h3>
                  <p className="mt-1.5 text-sm text-ink-muted leading-relaxed">
                    {capability.description}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Platform / roles */}
      <section id="platform" className="scroll-mt-20 border-b border-line">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-16 lg:py-20 grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div>
            <SectionLabel>Governance</SectionLabel>
            <h2 className="mt-4 text-2xl sm:text-3xl font-semibold tracking-tight text-ink">
              Role-governed by design
            </h2>
            <p className="mt-3 text-sm sm:text-base text-ink-muted leading-relaxed">
              Access is not an afterthought. TaskFlow models four delivery roles with explicit
              permissions, so people see exactly the work they are accountable for.
            </p>

            <ul className="mt-7 space-y-3.5">
              {[
                'Organization-scoped accounts with stable employee codes.',
                'Navigation and actions filtered by the signed-in role.',
                'Project membership and team leadership managed explicitly.',
                'Administrative provisioning separated from delivery work.',
              ].map((item) => (
                <li key={item} className="flex items-start gap-3 text-sm text-ink-muted">
                  <span className="mt-0.5 h-5 w-5 rounded-full bg-brand-50 border border-brand-100 flex items-center justify-center shrink-0">
                    <Check className="h-3 w-3 text-brand-700" aria-hidden="true" />
                  </span>
                  <span className="leading-relaxed">{item}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="rounded-2xl border border-line bg-surface shadow-card overflow-hidden">
            <div className="px-5 py-4 border-b border-line bg-subtle/60">
              <div className="flex items-center gap-2">
                <Layers className="h-4 w-4 text-brand-600" aria-hidden="true" />
                <p className="text-sm font-semibold text-ink">Delivery roles</p>
              </div>
              <p className="text-xs text-ink-muted mt-1">
                Permissions are enforced by the backend, not just the interface.
              </p>
            </div>
            <div className="divide-y divide-line">
              {ROLES.map((role) => (
                <div key={role.role} className="px-5 py-4 flex items-start gap-3">
                  <span
                    className={`mt-0.5 inline-flex shrink-0 rounded-md border px-2 py-0.5 text-[11px] font-medium ${role.tone}`}
                  >
                    {role.role}
                  </span>
                  <p className="text-xs text-ink-muted leading-relaxed">{role.scope}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Workflow */}
      <section id="workflow" className="scroll-mt-20 bg-subtle border-b border-line">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-16 lg:py-20">
          <div className="max-w-2xl">
            <SectionLabel>Workflow</SectionLabel>
            <h2 className="mt-4 text-2xl sm:text-3xl font-semibold tracking-tight text-ink">
              From scope to shipment
            </h2>
            <p className="mt-3 text-sm sm:text-base text-ink-muted leading-relaxed">
              A repeatable delivery rhythm that keeps planning, engineering, and quality in step.
            </p>
          </div>

          <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {WORKFLOW.map((item) => (
              <div
                key={item.step}
                className="relative rounded-xl border border-line bg-surface p-5 shadow-card"
              >
                <span className="text-[11px] font-mono font-semibold text-brand-600">
                  {item.step}
                </span>
                <h3 className="mt-2 text-sm font-semibold text-ink">{item.title}</h3>
                <p className="mt-1.5 text-xs text-ink-muted leading-relaxed">
                  {item.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Security */}
      <section id="security" className="scroll-mt-20 border-b border-line">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-16 lg:py-20">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {TRUST_POINTS.map((point) => {
              const Icon = point.icon;
              return (
                <div
                  key={point.title}
                  className="rounded-xl border border-line bg-surface p-5 shadow-card"
                >
                  <div className="flex items-center gap-2.5">
                    <span className="h-8 w-8 rounded-lg bg-brand-50 border border-brand-100 text-brand-600 flex items-center justify-center">
                      <Icon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <h3 className="text-sm font-semibold text-ink">{point.title}</h3>
                  </div>
                  <p className="mt-3 text-sm text-ink-muted leading-relaxed">
                    {point.description}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="bg-canvas">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-16">
          <div className="relative overflow-hidden rounded-2xl bg-brand-solid px-6 py-12 sm:px-12 text-center shadow-overlay">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 opacity-30"
              style={{
                backgroundImage:
                  'radial-gradient(circle at 20% 20%, rgba(255,255,255,0.35), transparent 45%), radial-gradient(circle at 80% 80%, rgba(255,255,255,0.25), transparent 45%)',
              }}
            />
            <div className="relative">
              <Workflow className="mx-auto h-7 w-7 text-white/80" aria-hidden="true" />
              <h2 className="mt-4 text-2xl sm:text-3xl font-semibold tracking-tight text-white">
                Bring your delivery workflow into one place
              </h2>
              <p className="mt-3 text-sm sm:text-base text-brand-50/90 max-w-xl mx-auto leading-relaxed">
                Sign in with your organization account to pick up where your team left off.
              </p>
              <div className="mt-7 flex flex-col sm:flex-row justify-center gap-3">
                <Link
                  to={primaryTarget}
                  className="inline-flex items-center justify-center gap-2 h-11 px-6 rounded-lg bg-white text-brand-700 text-sm font-semibold shadow-sm hover:bg-brand-50 transition-colors"
                >
                  {isBootstrapping ? 'Sign in' : primaryLabel}
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Link>
                <a
                  href="#platform"
                  className="inline-flex items-center justify-center h-11 px-6 rounded-lg border border-white/40 text-white text-sm font-medium hover:bg-white/10 transition-colors"
                >
                  Review governance
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-line bg-subtle">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8 py-12">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8">
            <div className="lg:col-span-2">
              <div className="flex items-center gap-2.5">
                <BrandMark size={28} />
                <span className="text-sm font-semibold tracking-tight text-ink">TaskFlow</span>
              </div>
              <p className="mt-3 text-xs text-ink-muted leading-relaxed max-w-sm">
                An enterprise project, sprint, and workflow platform for engineering
                organizations that value predictable delivery.
              </p>
            </div>

            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-ink-faint">
                Product
              </p>
              <ul className="mt-3 space-y-2 text-xs">
                <li>
                  <a href="#capabilities" className="text-ink-muted hover:text-ink transition-colors">
                    Capabilities
                  </a>
                </li>
                <li>
                  <a href="#workflow" className="text-ink-muted hover:text-ink transition-colors">
                    Workflow
                  </a>
                </li>
                <li>
                  <a href="#security" className="text-ink-muted hover:text-ink transition-colors">
                    Security
                  </a>
                </li>
              </ul>
            </div>

            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-ink-faint">
                Access
              </p>
              <ul className="mt-3 space-y-2 text-xs">
                <li>
                  <Link to="/login" className="text-ink-muted hover:text-ink transition-colors">
                    Sign in
                  </Link>
                </li>
                <li>
                  <Link to="/dashboard" className="text-ink-muted hover:text-ink transition-colors">
                    Workspace
                  </Link>
                </li>
                <li>
                  <a href="#platform" className="text-ink-muted hover:text-ink transition-colors">
                    Roles &amp; permissions
                  </a>
                </li>
              </ul>
            </div>
          </div>

          <div className="mt-10 pt-6 border-t border-line flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <p className="text-xs text-ink-faint">
              &copy; {new Date().getFullYear()} TaskFlow. Internal delivery platform.
            </p>
            <p className="inline-flex items-center gap-1.5 text-xs text-ink-faint">
              <ShieldCheck className="h-3.5 w-3.5 text-brand-600" aria-hidden="true" />
              Organization-scoped JWT authentication
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
