import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, CheckCircle2, Lock, Mail, User } from 'lucide-react';
import { authService } from '../../services/authService';
import { parseApiError } from '../../utils/errorHandler';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { BrandMark } from '../../components/common/BrandMark';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function ActivateAccountPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';

  const [loading, setLoading] = useState(true);
  const [validation, setValidation] = useState(null);
  const [validationError, setValidationError] = useState(null);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [activated, setActivated] = useState(false);

  useEffect(() => {
    let active = true;
    async function validate() {
      if (!token) {
        setValidationError('Activation token is missing.');
        setLoading(false);
        return;
      }
      try {
        const result = await authService.validateActivationToken(token);
        if (active) setValidation(result);
      } catch (err) {
        const parsed = parseApiError(err);
        if (active) setValidationError(parsed.message || 'Invalid or expired activation token.');
      } finally {
        if (active) setLoading(false);
      }
    }
    validate();
    return () => {
      active = false;
    };
  }, [token]);

  const validateForm = () => {
    const errors = {};
    if (!password) {
      errors.password = 'Password is required.';
    } else if (password.length < 8 || password.length > 128) {
      errors.password = 'Password must be between 8 and 128 characters.';
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password.';
    } else if (confirmPassword !== password) {
      errors.confirmPassword = 'Passwords do not match.';
    }
    return errors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setFormError(null);
    const errors = validateForm();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await authService.activateAccount({ token, password });
      setActivated(true);
    } catch (err) {
      const parsed = parseApiError(err);
      if (Object.keys(parsed.fieldErrors).length > 0) {
        setFieldErrors(parsed.fieldErrors);
      }
      setFormError(parsed.message || 'Unable to activate account.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-canvas flex items-center justify-center">
        <p className="text-sm text-ink-muted">Validating invitation…</p>
      </div>
    );
  }

  if (validationError) {
    return (
      <div className="min-h-screen bg-canvas text-ink flex items-center justify-center p-6">
        <div className="w-full max-w-md rounded-2xl bg-surface border border-line shadow-card p-8 text-center">
          <div className="mx-auto h-12 w-12 rounded-xl bg-red-50 border border-red-200 flex items-center justify-center text-red-600 mb-4">
            <AlertCircle className="h-6 w-6" aria-hidden="true" />
          </div>
          <h1 className="text-lg font-semibold text-ink">Invitation unavailable</h1>
          <p className="text-sm text-ink-muted mt-2 leading-relaxed">{validationError}</p>
          <Button
            variant="primary"
            className="mt-6"
            onClick={() => navigate('/login', { replace: true })}
          >
            Go to sign in
          </Button>
        </div>
      </div>
    );
  }

  if (activated) {
    return (
      <div className="min-h-screen bg-canvas text-ink flex items-center justify-center p-6">
        <div className="w-full max-w-md rounded-2xl bg-surface border border-line shadow-card p-8 text-center">
          <div className="mx-auto h-12 w-12 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-center text-emerald-600 mb-4">
            <CheckCircle2 className="h-6 w-6" aria-hidden="true" />
          </div>
          <h1 className="text-lg font-semibold text-ink">Account activated</h1>
          <p className="text-sm text-ink-muted mt-2 leading-relaxed">
            Your password has been set. You can now sign in to TaskFlow.
          </p>
          <Button
            variant="primary"
            className="mt-6"
            onClick={() => navigate('/login', { replace: true })}
          >
            Sign in
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-canvas text-ink flex items-center justify-center p-6">
      <div className="w-full max-w-md rounded-2xl bg-surface border border-line shadow-card p-7 sm:p-8">
        <div className="flex items-center gap-2.5 mb-6">
          <BrandMark size={28} />
          <span className="text-base font-semibold tracking-tight text-ink">TaskFlow</span>
        </div>
        <h1 className="text-xl font-semibold tracking-tight text-ink">Activate your account</h1>
        <p className="text-sm text-ink-muted mt-1.5 leading-relaxed">
          Welcome, {validation?.fullName}. Set a password to activate your workspace account
          ({validation?.email}).
        </p>

        {formError && (
          <div
            role="alert"
            className="mt-5 flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
            <span>{formError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
          <Input
            label="New Password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              if (fieldErrors.password) setFieldErrors((p) => ({ ...p, password: undefined }));
            }}
            error={fieldErrors.password}
            placeholder="Minimum 8 characters"
            leftIcon={<Lock className="h-4 w-4" />}
            disabled={submitting}
            required
          />
          <Input
            label="Confirm Password"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => {
              setConfirmPassword(e.target.value);
              if (fieldErrors.confirmPassword)
                setFieldErrors((p) => ({ ...p, confirmPassword: undefined }));
            }}
            error={fieldErrors.confirmPassword}
            placeholder="Repeat your password"
            leftIcon={<Lock className="h-4 w-4" />}
            disabled={submitting}
            required
          />
          <Button type="submit" variant="primary" size="lg" fullWidth loading={submitting}>
            {submitting ? 'Activating…' : 'Activate Account'}
          </Button>
        </form>

        <p className="mt-5 text-center text-xs text-ink-faint leading-relaxed">
          This activation link expires on{' '}
          {validation?.expiresAt ? new Date(validation.expiresAt).toLocaleString() : 'soon'}.
        </p>
      </div>
    </div>
  );
}
