import { useEffect, useRef, useState } from 'react';
import { AlertCircle, Briefcase, CheckCircle2, Mail, User } from 'lucide-react';
import { userService } from '../../services/userService';
import { ALL_ROLES, ROLE_META } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { useToast } from '../../hooks/useToast';
import { Avatar } from '../../components/common/Avatar';
import { Button } from '../../components/common/Button';
import { Select } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const AVATAR_SWATCHES = [
  '#0F9D8A',
  '#2E90FA',
  '#64748B',
  '#34D399',
  '#FBBF24',
  '#F97316',
  '#EF4444',
  '#94A3B8',
];

const ROLE_OPTIONS = ALL_ROLES.map((role) => ({
  value: role,
  label: ROLE_META[role]?.label || role,
}));

export function UserFormModal({
  isOpen,
  onClose,
  mode = 'create',
  initialUser = null,
  onSuccess,
}) {
  const isEdit = mode === 'edit';

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [roleName, setRoleName] = useState('DEVELOPER');
  const [avatarColor, setAvatarColor] = useState('#0F9D8A');
  const [invitationLink, setInvitationLink] = useState(null);
  const [copied, setCopied] = useState(false);
  const [copying, setCopying] = useState(false);
  const [copyError, setCopyError] = useState(null);
  const copiedTimerRef = useRef(null);
  const toast = useToast();

  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    if (isEdit && initialUser) {
      setFullName(initialUser.fullName || '');
      setEmail(initialUser.email || '');
      setJobTitle(initialUser.jobTitle || '');
      setRoleName(initialUser.role || 'DEVELOPER');
      setAvatarColor(initialUser.avatarColor || '#0F9D8A');
    } else {
      setFullName('');
      setEmail('');
      setJobTitle('');
      setRoleName('DEVELOPER');
      setAvatarColor('#0F9D8A');
    }
    setInvitationLink(null);
    setCopied(false);
    setCopying(false);
    setCopyError(null);
    if (copiedTimerRef.current) {
      clearTimeout(copiedTimerRef.current);
      copiedTimerRef.current = null;
    }
    setFieldErrors({});
    setFormError(null);
  }, [isOpen, isEdit, initialUser]);

  useEffect(
    () => () => {
      if (copiedTimerRef.current) {
        clearTimeout(copiedTimerRef.current);
        copiedTimerRef.current = null;
      }
    },
    []
  );

  const validate = () => {
    const errors = {};
    const trimmedName = fullName.trim();

    if (!trimmedName) {
      errors.fullName = 'Full name is required.';
    } else if (trimmedName.length < 2 || trimmedName.length > 120) {
      errors.fullName = 'Full name must be between 2 and 120 characters.';
    }

    if (!isEdit) {
      const trimmedEmail = email.trim();
      if (!trimmedEmail) {
        errors.email = 'Work email is required.';
      } else if (trimmedEmail.length > 180) {
        errors.email = 'Email must not exceed 180 characters.';
      } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
        errors.email = 'Enter a valid email address.';
      }

      if (!roleName) {
        errors.roleName = 'Role is required.';
      }
    }

    if (jobTitle.trim().length > 100) {
      errors.jobTitle = 'Job title must not exceed 100 characters.';
    }

    if (avatarColor.trim() && !/^#[0-9A-Fa-f]{6}$/.test(avatarColor.trim())) {
      errors.avatarColor = 'Select a valid 6-digit hex color.';
    }

    return errors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    setFormError(null);
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);

    try {
      let saved;
      if (isEdit && initialUser) {
        saved = await userService.updateUser(initialUser.id, {
          fullName,
          jobTitle,
          avatarColor,
        });
        onSuccess?.(saved);
        onClose?.();
      } else {
        const invitation = await userService.inviteUser({
          fullName,
          email,
          jobTitle,
          roleName,
          avatarColor,
        });
        setInvitationLink(invitation.activationLink);
        onSuccess?.(invitation.user);
      }
    } catch (err) {
      const parsed = parseApiError(err);
      if (Object.keys(parsed.fieldErrors).length > 0) {
        setFieldErrors(parsed.fieldErrors);
      }
      setFormError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  };

  const markCopied = () => {
    setCopied(true);
    if (copiedTimerRef.current) clearTimeout(copiedTimerRef.current);
    copiedTimerRef.current = setTimeout(() => {
      setCopied(false);
      copiedTimerRef.current = null;
    }, 2000);
  };

  const copyWithFallback = () => {
    const textarea = document.createElement('textarea');
    const activeElement = document.activeElement;
    textarea.value = invitationLink;
    textarea.setAttribute('readonly', '');
    textarea.setAttribute('aria-hidden', 'true');
    textarea.style.position = 'fixed';
    textarea.style.top = '0';
    textarea.style.left = '0';
    textarea.style.width = '1px';
    textarea.style.height = '1px';
    textarea.style.opacity = '0';
    textarea.style.pointerEvents = 'none';

    document.body.appendChild(textarea);
    let succeeded = false;
    try {
      textarea.focus();
      textarea.select();
      if (textarea.setSelectionRange) {
        textarea.setSelectionRange(0, textarea.value.length);
      }
      succeeded = document.execCommand('copy');
    } catch (fallbackError) {
      succeeded = false;
    } finally {
      if (textarea.parentNode) {
        textarea.parentNode.removeChild(textarea);
      }
      if (activeElement && typeof activeElement.focus === 'function') {
        activeElement.focus();
      }
    }
    return succeeded;
  };

  const handleCopy = async () => {
    if (!invitationLink || copying) return;

    setCopying(true);
    setCopyError(null);

    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        try {
          await navigator.clipboard.writeText(invitationLink);
          markCopied();
          toast.success('Activation link copied to clipboard.', 'Copied');
          return;
        } catch (clipboardError) {
          // Permission denied or API rejected — continue to the fallback below.
        }
      }

      if (!copyWithFallback()) {
        throw new Error('Clipboard fallback unavailable');
      }

      markCopied();
      toast.success('Activation link copied to clipboard.', 'Copied');
    } catch (copyFailure) {
      setCopied(false);
      setCopyError('Unable to copy automatically. Select the link above and copy it manually.');
      toast.error('Unable to copy automatically. Select the link above and copy it manually.', 'Copy failed');
    } finally {
      setCopying(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      title={isEdit ? 'Edit Employee Profile' : 'Create Employee Account'}
      description={
        isEdit
          ? `Update permitted profile details for ${initialUser?.email || 'this employee'}.`
          : 'Provision a new employee inside your TaskFlow organization.'
      }
      size="md"
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          {!invitationLink && (
            <Button
              type="submit"
              form="taskflow-user-form"
              variant="primary"
              size="sm"
              loading={submitting}
            >
              {isEdit ? 'Save Changes' : 'Create Employee'}
            </Button>
          )}
        </>
      }
    >
      {invitationLink ? (
        <div className="space-y-4">
          <div
            role="status"
            className="flex items-start gap-2.5 rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-3 text-xs text-emerald-800"
          >
            <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0 mt-0.5" aria-hidden="true" />
            <div>
              <p className="font-semibold">Invitation created</p>
              <p className="mt-0.5 leading-relaxed">
                Share this secure activation link with the employee. It expires in 24 hours and
                can only be used once.
              </p>
            </div>
          </div>
          <div className="rounded-lg border border-line bg-subtle/60 p-3 select-all">
            <p className="text-[11px] font-mono text-ink break-all">{invitationLink}</p>
          </div>
          {copyError && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
            >
              <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
              <span>{copyError}</span>
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={handleCopy}
              disabled={copying || !invitationLink}
            >
              {copying ? 'Copying…' : copied ? 'Copied' : 'Copy link'}
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => {
                setInvitationLink(null);
                onClose?.();
              }}
            >
              Done
            </Button>
          </div>
        </div>
      ) : (
      <form id="taskflow-user-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
            <span>{formError}</span>
          </div>
        )}

        {/* Live avatar preview & color selection */}
        <div className="flex items-center gap-4 p-3.5 rounded-lg bg-subtle/70 border border-line">
          <Avatar name={fullName || 'New User'} color={avatarColor} size="lg" />
          <div className="flex-1 min-w-0">
            <p className="text-xs font-medium text-ink-muted mb-2">Avatar Accent</p>
            <div className="flex flex-wrap items-center gap-2">
              {AVATAR_SWATCHES.map((hex) => {
                const selected = avatarColor.toUpperCase() === hex.toUpperCase();
                return (
                  <button
                    key={hex}
                    type="button"
                    disabled={submitting}
                    onClick={() => setAvatarColor(hex)}
                    aria-label={`Select color ${hex}`}
                    className={[
                      'h-6 w-6 rounded-md transition-transform',
                      selected
                        ? 'ring-2 ring-offset-2 ring-offset-subtle ring-brand-200 scale-105'
                        : 'opacity-80 hover:opacity-100',
                    ].join(' ')}
                    style={{ backgroundColor: hex }}
                  />
                );
              })}
            </div>
          </div>
        </div>

        <Input
          label="Full Name"
          name="fullName"
          value={fullName}
          onChange={(e) => {
            setFullName(e.target.value);
            if (fieldErrors.fullName) setFieldErrors((p) => ({ ...p, fullName: undefined }));
          }}
          error={fieldErrors.fullName}
          placeholder="e.g. Priya Nair"
          leftIcon={<User className="h-4 w-4" />}
          disabled={submitting}
          required
        />

        {!isEdit && (
          <>
            <Input
              label="Work Email"
              name="email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldErrors.email) setFieldErrors((p) => ({ ...p, email: undefined }));
              }}
              error={fieldErrors.email}
              placeholder="priya.nair@company.com"
              leftIcon={<Mail className="h-4 w-4" />}
              disabled={submitting}
              required
            />

            <Select
              label="Workspace Role"
              name="roleName"
              value={roleName}
              onChange={(e) => {
                setRoleName(e.target.value);
                if (fieldErrors.roleName) setFieldErrors((p) => ({ ...p, roleName: undefined }));
              }}
              options={ROLE_OPTIONS}
              error={fieldErrors.roleName}
              disabled={submitting}
              required
            />
          </>
        )}

        <Input
          label="Job Title"
          name="jobTitle"
          value={jobTitle}
          onChange={(e) => {
            setJobTitle(e.target.value);
            if (fieldErrors.jobTitle) setFieldErrors((p) => ({ ...p, jobTitle: undefined }));
          }}
          error={fieldErrors.jobTitle}
          placeholder="e.g. Senior Backend Engineer"
          leftIcon={<Briefcase className="h-4 w-4" />}
          disabled={submitting}
        />

        {isEdit && (
          <p className="text-[11px] text-ink-faint">
            Email address and employee code are immutable. Role and activation status are managed
            separately by Administrators.
          </p>
        )}
      </form>
      )}
    </Modal>
  );
}
