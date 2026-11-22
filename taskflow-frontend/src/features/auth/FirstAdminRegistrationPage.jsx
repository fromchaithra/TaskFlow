import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { AlertCircle, ArrowLeft, Building2, CheckCircle2, Lock, Mail, ShieldCheck, User } from 'lucide-react';
import { authService } from '../../services/authService';
import { parseApiError } from '../../utils/errorHandler';
import { BrandMark } from '../../components/common/BrandMark';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate({ fullName, organizationName, email, password, confirmPassword }) {
  const errors = {};
  const name = fullName.trim();
  const mail = email.trim();

  if (!name) errors.fullName = 'Full name is required.';
  else if (name.length < 2 || name.length > 120) errors.fullName = 'Full name must be between 2 and 120 characters.';

  if (organizationName?.trim().length > 150) {
    errors.organizationName = 'Organization name must not exceed 150 characters.';
  }

  if (!mail) errors.email = 'Email is required.';
  else if (mail.length > 180) errors.email = 'Email must not exceed 180 characters.';
  else if (!EMAIL_PATTERN.test(mail)) errors.email = 'Enter a valid email address.';

  if (!password) errors.password = 'Password is required.';
  else if (password.length < 8) errors.password = 'Password must be at least 8 characters.';
  else if (password.length > 128) errors.password = 'Password must not exceed 128 characters.';

  if (!confirmPassword) errors.confirmPassword = 'Please confirm your password.';
  else if (password !== confirmPassword) errors.confirmPassword = 'Passwords do not match.';

  return errors;
}

export function FirstAdminRegistrationPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '',
    organizationName: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const fieldRefs = useRef({});

  const focusFirstError = (errors) => {
    const firstKey = ['fullName', 'organizationName', 'email', 'password', 'confirmPassword']
      .find((key) => errors[key]);
    if (firstKey) fieldRefs.current[firstKey]?.focus();
  };

  const updateField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (fieldErrors[key]) setFieldErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setFormError(null);
    const errors = validate(form);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      focusFirstError(errors);
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await authService.registerFirstAdmin(form);
      navigate('/login', { replace: true, state: { registrationComplete: true } });
    } catch (err) {
      const parsed = parseApiError(err);
      setFieldErrors(parsed.fieldErrors || {});
      focusFirstError(parsed.fieldErrors || {});
      setFormError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-canvas text-ink grid grid-cols-1 lg:grid-cols-12">
      <section className="hidden lg:flex lg:col-span-5 flex-col justify-between bg-brand-50/60 p-10 xl:p-14 border-r border-line relative overflow-hidden">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 tf-grid-pattern tf-grid-fade opacity-70" />
        <div className="relative z-10 flex items-center gap-3">
          <BrandMark size={34} />
          <div>
            <p className="text-base font-semibold tracking-tight leading-none">TaskFlow</p>
            <p className="mt-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-ink-faint">Enterprise delivery</p>
          </div>
        </div>
        <div className="relative z-10 my-auto space-y-5 py-12">
          <span className="inline-flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-brand-700">
            <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
            Public registration
          </span>
          <h1 className="text-[2rem] leading-[1.2] font-semibold tracking-tight text-ink">
            Your own workspace, in a few seconds.
          </h1>
          <p className="max-w-md text-sm leading-relaxed text-ink-muted">
            Every signup creates a new independent organization and its first Administrator.
            Your workspace and data stay completely separate from every other organization.
          </p>
        </div>
        <div className="relative z-10 flex items-center gap-2 text-xs text-ink-faint">
          <CheckCircle2 className="h-3.5 w-3.5 text-brand-600" aria-hidden="true" />
          Passwords are stored as BCrypt hashes.
        </div>
      </section>

      <main className="lg:col-span-7 flex flex-col p-6 sm:p-10">
        <div className="flex items-center justify-between gap-3">
          <Link to="/" className="inline-flex items-center gap-1.5 text-xs font-medium text-ink-muted hover:text-brand-700">
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            Back to welcome
          </Link>
          <div className="flex items-center gap-2 lg:hidden">
            <BrandMark size={24} />
            <span className="text-sm font-semibold tracking-tight">TaskFlow</span>
          </div>
        </div>

        <div className="flex flex-1 items-center justify-center">
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            className="w-full max-w-[460px] py-10"
          >
            <div className="rounded-2xl border border-line bg-surface p-7 shadow-card sm:p-8">
              <div className="mb-6">
                <h2 className="text-xl font-semibold tracking-tight text-ink">Create your organization</h2>
                <p className="mt-1.5 text-sm leading-relaxed text-ink-muted">
                  Register as the first Administrator of a new TaskFlow workspace.
                </p>
              </div>

              <div
                role="note"
                className="mb-5 flex items-start gap-2.5 rounded-lg border border-brand-100 bg-brand-50/60 px-3.5 py-3 text-xs text-ink-muted"
              >
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" aria-hidden="true" />
                <span>
                  This creates a brand-new, isolated organization with you as its Administrator.
                  You can then invite and manage members inside your own workspace.
                </span>
              </div>

              {formError && (
                <div role="alert" className="mb-5 flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" aria-hidden="true" />
                  <span>{formError}</span>
                </div>
              )}

              <form onSubmit={handleSubmit} noValidate className="space-y-4">
                <Input
                  ref={(node) => { fieldRefs.current.fullName = node; }}
                  label="Full Name"
                  name="fullName"
                  autoComplete="name"
                  placeholder="Jane Administrator"
                  value={form.fullName}
                  onChange={(e) => updateField('fullName', e.target.value)}
                  error={fieldErrors.fullName}
                  leftIcon={<User className="h-4 w-4" />}
                  disabled={submitting}
                  required
                />
                <Input
                  ref={(node) => { fieldRefs.current.organizationName = node; }}
                  label="Organization Name"
                  name="organizationName"
                  autoComplete="organization"
                  placeholder="Acme Engineering"
                  helperText="Optional — a name is generated from your full name if left blank."
                  value={form.organizationName}
                  onChange={(e) => updateField('organizationName', e.target.value)}
                  error={fieldErrors.organizationName}
                  leftIcon={<Building2 className="h-4 w-4" />}
                  disabled={submitting}
                />
                <Input
                  ref={(node) => { fieldRefs.current.email = node; }}
                  label="Work Email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  placeholder="admin@company.com"
                  value={form.email}
                  onChange={(e) => updateField('email', e.target.value)}
                  error={fieldErrors.email}
                  leftIcon={<Mail className="h-4 w-4" />}
                  disabled={submitting}
                  required
                />
                <Input
                  ref={(node) => { fieldRefs.current.password = node; }}
                  label="Password"
                  name="password"
                  type="password"
                  autoComplete="new-password"
                  placeholder="At least 8 characters"
                  value={form.password}
                  onChange={(e) => updateField('password', e.target.value)}
                  error={fieldErrors.password}
                  leftIcon={<Lock className="h-4 w-4" />}
                  disabled={submitting}
                  required
                />
                <Input
                  ref={(node) => { fieldRefs.current.confirmPassword = node; }}
                  label="Confirm Password"
                  name="confirmPassword"
                  type="password"
                  autoComplete="new-password"
                  placeholder="Re-enter password"
                  value={form.confirmPassword}
                  onChange={(e) => updateField('confirmPassword', e.target.value)}
                  error={fieldErrors.confirmPassword}
                  leftIcon={<Lock className="h-4 w-4" />}
                  disabled={submitting}
                  required
                />
                <div className="pt-2">
                  <Button type="submit" variant="primary" size="lg" fullWidth loading={submitting}>
                    {submitting ? 'Creating workspace…' : 'Create Organization & Admin'}
                  </Button>
                </div>
              </form>
              <p className="mt-5 text-center text-xs leading-relaxed text-ink-faint">
                Already have an account?{' '}
                <Link to="/login" className="font-semibold text-brand-700 hover:underline">Sign in</Link>
              </p>
            </div>
          </motion.div>
        </div>
      </main>
    </div>
  );
}
