import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  LogIn,
  ShieldCheck,
  Sparkles,
  UserPlus,
  Workflow,
} from 'lucide-react';
import { BrandMark } from '../../components/common/BrandMark';

const CHOICES = [
  {
    to: '/register',
    icon: UserPlus,
    eyebrow: 'New to TaskFlow?',
    title: 'Create Account',
    description: 'Create a new independent organization and become its Administrator.',
    cta: 'Get started',
    featured: true,
  },
  {
    to: '/login',
    icon: LogIn,
    eyebrow: 'Already have an account?',
    title: 'Login',
    description: 'Sign in with the credentials issued by your organization administrator.',
    cta: 'Sign in',
    featured: false,
  },
];

export function WelcomePage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-canvas text-ink">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 tf-grid-pattern tf-grid-fade opacity-70" />
      <div aria-hidden="true" className="pointer-events-none absolute -top-40 -left-32 h-[28rem] w-[28rem] rounded-full bg-brand-100/70 blur-3xl" />
      <div aria-hidden="true" className="pointer-events-none absolute -bottom-48 -right-24 h-[30rem] w-[30rem] rounded-full bg-brand-50 blur-3xl" />

      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-6 sm:px-8">
        <div className="flex items-center gap-3">
          <BrandMark size={34} />
          <div>
            <p className="text-base font-semibold tracking-tight leading-none">TaskFlow</p>
            <p className="mt-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-ink-faint">
              Enterprise delivery
            </p>
          </div>
        </div>
        <span className="hidden items-center gap-2 rounded-full border border-brand-100 bg-surface/80 px-3 py-1.5 text-xs text-brand-700 shadow-card sm:inline-flex">
          <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
          Secure workspace
        </span>
      </header>

      <main className="relative z-10 mx-auto flex w-full max-w-6xl flex-1 flex-col px-5 pb-16 pt-8 sm:px-8 sm:pt-14">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25, ease: 'easeOut' }}
          className="mx-auto max-w-2xl text-center"
        >
          <span className="inline-flex items-center gap-2 rounded-full border border-brand-100 bg-brand-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-brand-700">
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
            Welcome to TaskFlow
          </span>
          <h1 className="mt-5 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Plan, execute, and verify work in one place.
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-sm leading-relaxed text-ink-muted sm:text-base">
            A role-governed delivery workspace for projects, tasks, sprints, defects, and QA —
            with the clarity your team needs to ship confidently.
          </p>
        </motion.div>

        <div className="mx-auto mt-12 grid w-full max-w-4xl grid-cols-1 gap-5 md:grid-cols-2">
          {CHOICES.map((choice, index) => {
            const Icon = choice.icon;
            return (
              <motion.div
                key={choice.to}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.25, delay: 0.06 * index, ease: 'easeOut' }}
              >
                <Link
                  to={choice.to}
                  className={`group flex h-full flex-col justify-between rounded-2xl border p-6 shadow-card transition-all hover:-translate-y-0.5 hover:shadow-card-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 sm:p-7 ${
                    choice.featured
                      ? 'border-brand-200 bg-gradient-to-br from-brand-50 to-surface'
                      : 'border-line bg-surface'
                  }`}
                >
                  <div>
                    <div
                      className={`flex h-11 w-11 items-center justify-center rounded-xl border ${
                        choice.featured
                          ? 'border-brand-200 bg-brand-500 text-white'
                          : 'border-line bg-subtle text-brand-700'
                      }`}
                    >
                      <Icon className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <p className="mt-6 text-[11px] font-semibold uppercase tracking-[0.14em] text-ink-faint">
                      {choice.eyebrow}
                    </p>
                    <h2 className="mt-2 text-xl font-semibold tracking-tight text-ink">{choice.title}</h2>
                    <p className="mt-2 text-sm leading-relaxed text-ink-muted">{choice.description}</p>
                  </div>
                  <span className="mt-7 inline-flex items-center gap-2 text-sm font-semibold text-brand-700">
                    {choice.cta}
                    <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" aria-hidden="true" />
                  </span>
                </Link>
              </motion.div>
            );
          })}
        </div>

        <div className="mx-auto mt-10 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-xs text-ink-faint">
          <span className="inline-flex items-center gap-1.5">
            <Workflow className="h-3.5 w-3.5" aria-hidden="true" />
            Built for modern delivery teams
          </span>
          <span>Organization-scoped access</span>
          <span>Enterprise security</span>
        </div>
      </main>
    </div>
  );
}
