import { useState } from 'react';
import { AlertCircle, Lock, Mail, User, UserPlus } from 'lucide-react';
import { userService } from '../../services/userService';
import { ROLES, ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { Button } from '../../components/common/Button';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { useToast } from '../../hooks/useToast';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// This modal is rendered only for an authenticated ADMIN. Allowing another
// ADMIN here is safe because the backend re-checks the actor's role and always
// assigns the actor's own organization.
const REGISTRABLE_ROLES = [ROLES.ADMIN, ROLES.PROJECT_MANAGER, ROLES.DEVELOPER, ROLES.QA_TESTER];
const ROLE_OPTIONS = REGISTRABLE_ROLES.map((role) => ({
  value: role,
  label: ROLE_META[role]?.label || role,
}));

function validate({ fullName, email, password, confirmPassword, roleName }) {
  const errors = {};
  const name = fullName.trim();
  const mail = email.trim();

  if (!name) errors.fullName = 'Full name is required.';
  else if (name.length < 2 || name.length > 120) errors.fullName = 'Full name must be between 2 and 120 characters.';

  if (!mail) errors.email = 'Email is required.';
  else if (mail.length > 180) errors.email = 'Email must not exceed 180 characters.';
  else if (!EMAIL_PATTERN.test(mail)) errors.email = 'Enter a valid email address.';

  if (!REGISTRABLE_ROLES.includes(roleName)) errors.roleName = 'Select a valid role.';

  if (!password) errors.password = 'Password is required.';
  else if (password.length < 8) errors.password = 'Password must be at least 8 characters.';
  else if (password.length > 128) errors.password = 'Password must not exceed 128 characters.';

  if (!confirmPassword) errors.confirmPassword = 'Please confirm your password.';
  else if (password !== confirmPassword) errors.confirmPassword = 'Passwords do not match.';

  return errors;
}

const EMPTY_FORM = {
  fullName: '',
  email: '',
  roleName: ROLES.DEVELOPER,
  password: '',
  confirmPassword: '',
};

export function RegisterUserModal({ isOpen, onClose, onSuccess }) {
  const toast = useToast();
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const updateField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (fieldErrors[key]) setFieldErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const reset = () => {
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setSubmitting(false);
  };

  const handleClose = () => {
    if (submitting) return;
    reset();
    onClose?.();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);
    const errors = validate(form);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      const created = await userService.createUser({
        fullName: form.fullName,
        email: form.email,
        password: form.password,
        roleName: form.roleName,
        jobTitle: null,
        avatarColor: '#0F9D8A',
      });
      toast.success(`${created.fullName} can now sign in.`, 'Account created');
      reset();
      onSuccess?.();
      onClose?.();
    } catch (err) {
      const parsed = parseApiError(err);
      setFieldErrors(parsed.fieldErrors || {});
      setFormError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Register User"
      description="Create an employee account in your organization. Administrators cannot be created here."
      size="md"
    >
      {formError && (
        <div role="alert" className="mb-4 flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" aria-hidden="true" />
          <span>{formError}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <Input
          label="Full Name"
          name="fullName"
          autoComplete="name"
          placeholder="Jane Doe"
          value={form.fullName}
          onChange={(e) => updateField('fullName', e.target.value)}
          error={fieldErrors.fullName}
          leftIcon={<User className="h-4 w-4" />}
          disabled={submitting}
          required
        />
        <Input
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="name@company.com"
          value={form.email}
          onChange={(e) => updateField('email', e.target.value)}
          error={fieldErrors.email}
          leftIcon={<Mail className="h-4 w-4" />}
          disabled={submitting}
          required
        />
        <Select
          id="roleName"
          label="Role"
          options={ROLE_OPTIONS}
          value={form.roleName}
          onChange={(e) => updateField('roleName', e.target.value)}
          error={fieldErrors.roleName}
          disabled={submitting}
          required
        />
        <Input
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
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={submitting} leftIcon={<UserPlus className="h-4 w-4" />}>
            Register User
          </Button>
        </div>
      </form>
    </Modal>
  );
}
