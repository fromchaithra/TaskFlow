import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock,
  Lock,
  Mail,
  ShieldCheck,
  Workflow,
  Layers,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { parseApiError } from '../../utils/errorHandler';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { BrandMark } from '../../components/common/BrandMark';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const PLATFORM_PILLARS = [
  {
    icon: Layers,
    title: 'Organization & role isolation',
    description:
      'Scoped workspaces enforcing Admin, Project Manager, Developer, and QA Tester permissions.',
  },
  {
    icon: Workflow,
    title: 'Controlled delivery workflows',
    description:
      'Structured project lifecycles, Kanban transitions, sprint cycles, and defect verification.',
  },
  {
    icon: ShieldCheck,
    title: 'Stateless token security',
    description:
      'Short-lived access tokens paired with rotating HttpOnly refresh sessions.',
  },
];

function validateForm({ email, password }) {
  const errors = {};
  const trimmedEmail = email.trim();

  if (!trimmedEmail) {
    errors.email = 'Work email is required.';
  } else if (trimmedEmail.length > 180) {
    errors.email = 'Email must not exceed 180 characters.';
  } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
    errors.email = 'Enter a valid email address.';
  }

  if (!password) {
    errors.password = 'Password is required.';
  } else if (password.length < 8) {
    errors.password = 'Password must be at least 8 characters.';
  } else if (password.length > 128) {
    errors.password = 'Password must not exceed 128 characters.';
  }

  return errors;
}

export function LoginPage() {
  const { isAuthenticated, isBootstrapping, sessionExpired, clearSessionExpired, login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const emailRef = useRef(null);
  const passwordRef = useRef(null);

  const redirectTarget = location.state?.from?.pathname || '/dashboard';

  // Move focus to the first invalid field so keyboard users can correct it immediately.
  const focusFirstError = (errors) => {
    if (errors.email) emailRef.current?.focus();
    else if (errors.password) passwordRef.current?.focus();
  };

  useEffect(() => {
    return () => {
      if (sessionExpired) {
        clearSessionExpired();
      }
    };
  }, [sessionExpired, clearSessionExpired]);

  if (!isBootstrapping && isAuthenticated) {
    return <Navigate to={redirectTarget} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setFormError(null);

    const validationErrors = validateForm({ email, password });
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors);
      focusFirstError(validationErrors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);

    try {
      const response = await login({ email, password });
      const firstName = response?.user?.fullName?.split(' ')[0] || 'back';
      toast.success(`Signed in as ${response?.user?.fullName || email}.`, `Welcome ${firstName}`);
      navigate(redirectTarget, { replace: true });
    } catch (err) {
      const parsed = parseApiError(err);
      if (Object.keys(parsed.fieldErrors).length > 0) {
        setFieldErrors(parsed.fieldErrors);
        focusFirstError(parsed.fieldErrors);
      }
      setFormError({
        code: parsed.code,
        message: parsed.message,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-canvas text-ink grid grid-cols-1 lg:grid-cols-12">
      {/* Left brand panel */}
      <section className="hidden lg:flex lg:col-span-5 flex-col justify-between p-10 xl:p-14 bg-brand-50/60 border-r border-line relative overflow-hidden">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 tf-grid-pattern tf-grid-fade opacity-70"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-28 -left-24 h-80 w-80 rounded-full bg-brand-200/40 blur-3xl"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-32 -right-16 h-72 w-72 rounded-full bg-brand-100/60 blur-3xl"
        />

        {/* Brand */}
        <div className="relative z-10 flex items-center gap-3">
          <BrandMark size={34} />
          <div>
            <p className="text-base font-semibold tracking-tight text-ink leading-none">
              TaskFlow
            </p>
            <p className="text-[11px] font-mono uppercase tracking-[0.16em] text-ink-faint mt-1.5">
              Enterprise delivery
            </p>
          </div>
        </div>

        {/* Messaging */}
        <div className="relative z-10 space-y-8 my-auto py-12">
          <div className="space-y-4">
            <p className="inline-flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-brand-700">
              <span className="h-1.5 w-1.5 rounded-full bg-brand-500" aria-hidden="true" />
              Unified engineering workspace
            </p>
            <h1 className="text-[2rem] leading-[1.2] font-semibold tracking-tight text-ink">
              Operational clarity for engineering teams.
            </h1>
            <p className="text-sm text-ink-muted leading-relaxed max-w-md">
              Coordinate organizations, projects, sprint execution, Kanban boards, and QA
              verification from a single role-governed workspace.
            </p>
          </div>

          <div className="space-y-4 pt-6 border-t border-brand-200/70">
            {PLATFORM_PILLARS.map((pillar) => {
              const Icon = pillar.icon;
              return (
                <div key={pillar.title} className="flex items-start gap-3.5">
                  <div className="h-9 w-9 rounded-lg bg-surface border border-brand-100 text-brand-600 flex items-center justify-center shrink-0 shadow-card">
                    <Icon className="h-4 w-4" aria-hidden="true" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-ink">{pillar.title}</p>
                    <p className="text-xs text-ink-muted mt-0.5 leading-relaxed">
                      {pillar.description}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Footer note */}
        <div className="relative z-10 flex items-center gap-2 text-xs text-ink-faint">
          <CheckCircle2 className="h-3.5 w-3.5 text-brand-600 shrink-0" aria-hidden="true" />
          <span>Protected by organization-scoped JWT authentication</span>
        </div>
      </section>

      {/* Right login form */}
      <main className="lg:col-span-7 flex flex-col p-6 sm:p-10">
        <div className="w-full flex items-center justify-between gap-3">
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-muted hover:text-brand-700 transition-colors"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            Back to homepage
          </Link>
          <div className="flex lg:hidden items-center gap-2">
            <BrandMark size={24} />
            <span className="text-sm font-semibold tracking-tight text-ink">TaskFlow</span>
          </div>
        </div>

        <div className="flex-1 flex items-center justify-center">
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            className="w-full max-w-[420px] py-10"
          >
            <div className="rounded-2xl bg-surface border border-line shadow-card p-7 sm:p-8">
              <div className="mb-6">
                <p className="mb-2 inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-brand-700">
                  <span className="h-1.5 w-1.5 rounded-full bg-brand-500" aria-hidden="true" />
                  Welcome back
                </p>
                <h2 className="text-xl font-semibold tracking-tight text-ink">
                  Sign in to your workspace
                </h2>
                <p className="text-sm text-ink-muted mt-1.5 leading-relaxed">
                  Use the credentials issued by your organization administrator.
                </p>
              </div>

              {location.state?.registrationComplete && (
                <div
                  role="status"
                  className="mb-5 flex items-start gap-2.5 rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-3 text-xs text-emerald-800"
                >
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
                  <div>
                    <p className="font-semibold">Administrator account created</p>
                    <p className="mt-0.5 text-emerald-700">Sign in with the credentials you just set.</p>
                  </div>
                </div>
              )}

              {sessionExpired && (
                <div
                  role="status"
                  className="mb-5 flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-3 text-xs text-amber-800"
                >
                  <Clock className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" aria-hidden="true" />
                  <div>
                    <p className="font-semibold">Session expired</p>
                    <p className="mt-0.5 text-amber-700">
                      Your previous session ended. Sign in again to resume your work.
                    </p>
                  </div>
                </div>
              )}

              {formError && (
                <div
                  role="alert"
                  className="mb-5 flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
                >
                  <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
                  <div>
                    <p className="font-semibold">
                      {formError.code === 'ACCOUNT_DEACTIVATED'
                        ? 'Account deactivated'
                        : 'Unable to sign in'}
                    </p>
                    <p className="mt-0.5 leading-relaxed">{formError.message}</p>
                  </div>
                </div>
              )}

              <form onSubmit={handleSubmit} noValidate className="space-y-4">
                <Input
                  ref={emailRef}
                  label="Work Email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  placeholder="name@company.com"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (fieldErrors.email) {
                      setFieldErrors((prev) => ({ ...prev, email: undefined }));
                    }
                  }}
                  error={fieldErrors.email}
                  leftIcon={<Mail className="h-4 w-4" />}
                  disabled={submitting}
                  required
                />

                <Input
                  ref={passwordRef}
                  label="Password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (fieldErrors.password) {
                      setFieldErrors((prev) => ({ ...prev, password: undefined }));
                    }
                  }}
                  error={fieldErrors.password}
                  leftIcon={<Lock className="h-4 w-4" />}
                  disabled={submitting}
                  required
                />

                <div className="pt-2">
                  <Button
                    type="submit"
                    variant="primary"
                    size="lg"
                    fullWidth
                    loading={submitting}
                  >
                    {submitting ? 'Signing in...' : 'Sign In'}
                  </Button>
                </div>
              </form>
            </div>

            <p className="mt-5 text-center text-xs text-ink-faint leading-relaxed">
              Accounts are provisioned by your organization administrator. Contact your workspace
              admin if you need access or a password reset.
            </p>
          </motion.div>
        </div>
      </main>
    </div>
  );
}
