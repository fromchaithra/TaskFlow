import { useCallback, useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  Building2,
  CalendarDays,
  Clock,
  Globe,
  Hash,
  RefreshCw,
  RotateCcw,
  Save,
  Shield,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { organizationService } from '../../services/organizationService';
import { userService } from '../../services/userService';
import { ROLE_META, ROLES } from '../../utils/constants';
import { parseApiError } from '../../utils/errorHandler';
import { formatDateTime } from '../../utils/formatters';
import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody, CardFooter, CardHeader } from '../../components/common/Card';
import { Input } from '../../components/common/Input';
import { ErrorState, Skeleton } from '../../components/common/Loading';
import { PageHeader } from '../../components/common/PageHeader';

const WEEKDAYS = [
  { code: 'MON', label: 'Mon' },
  { code: 'TUE', label: 'Tue' },
  { code: 'WED', label: 'Wed' },
  { code: 'THU', label: 'Thu' },
  { code: 'FRI', label: 'Fri' },
  { code: 'SAT', label: 'Sat' },
  { code: 'SUN', label: 'Sun' },
];

const COMMON_TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'Asia/Dubai',
  'Asia/Kolkata',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
];

function parseWorkingDays(value = '') {
  return value
    .split(',')
    .map((d) => d.trim().toUpperCase())
    .filter((code) => WEEKDAYS.some((w) => w.code === code));
}

function serializeWorkingDays(daysSet) {
  return WEEKDAYS.filter((w) => daysSet.includes(w.code))
    .map((w) => w.code)
    .join(',');
}

export function OrganizationPage() {
  const { hasRole, refreshCurrentUser } = useAuth();
  const toast = useToast();
  const isAdmin = hasRole(ROLES.ADMIN);

  const [organization, setOrganization] = useState(null);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [name, setName] = useState('');
  const [industry, setIndustry] = useState('');
  const [timezone, setTimezone] = useState('UTC');
  const [workingDaysList, setWorkingDaysList] = useState(['MON', 'TUE', 'WED', 'THU', 'FRI']);

  const [fieldErrors, setFieldErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [saving, setSaving] = useState(false);

  const syncFormFromOrg = useCallback((org) => {
    setName(org?.name || '');
    setIndustry(org?.industry || '');
    setTimezone(org?.timezone || 'UTC');
    setWorkingDaysList(parseWorkingDays(org?.workingDays || 'MON,TUE,WED,THU,FRI'));
    setFieldErrors({});
    setSubmitError(null);
  }, []);

  const fetchOrganization = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await organizationService.getCurrentOrganization();
      setOrganization(data);
      syncFormFromOrg(data);
    } catch (err) {
      setError(parseApiError(err));
    } finally {
      setLoading(false);
    }
  }, [syncFormFromOrg]);

  useEffect(() => {
    fetchOrganization();
  }, [fetchOrganization]);

  // Reuse the existing users endpoint; it is only available to member managers.
  useEffect(() => {
    let active = true;
    if (!hasRole([ROLES.ADMIN, ROLES.PROJECT_MANAGER])) {
      setMembers([]);
      return undefined;
    }
    userService.listUsers({ page: 0, size: 6, sortBy: 'fullName', sortDir: 'asc' })
      .then((response) => {
        if (active) setMembers(Array.isArray(response?.content) ? response.content : []);
      })
      .catch(() => {
        if (active) setMembers([]);
      });
    return () => { active = false; };
  }, [hasRole]);

  const serializedDays = useMemo(
    () => serializeWorkingDays(workingDaysList),
    [workingDaysList]
  );

  const isDirty = useMemo(() => {
    if (!organization) return false;
    return (
      name.trim() !== (organization.name || '') ||
      industry.trim() !== (organization.industry || '') ||
      timezone.trim() !== (organization.timezone || '') ||
      serializedDays !== (organization.workingDays || '').toUpperCase()
    );
  }, [organization, name, industry, timezone, serializedDays]);

  const toggleDay = (code) => {
    setWorkingDaysList((prev) =>
      prev.includes(code) ? prev.filter((item) => item !== code) : [...prev, code]
    );
    if (fieldErrors.workingDays) {
      setFieldErrors((prev) => ({ ...prev, workingDays: undefined }));
    }
  };

  const validate = () => {
    const errors = {};
    if (!name.trim()) {
      errors.name = 'Organization name is required.';
    } else if (name.trim().length > 150) {
      errors.name = 'Organization name must not exceed 150 characters.';
    }

    if (industry.trim().length > 100) {
      errors.industry = 'Industry must not exceed 100 characters.';
    }

    if (!timezone.trim()) {
      errors.timezone = 'IANA timezone is required.';
    } else if (timezone.trim().length > 64) {
      errors.timezone = 'Timezone must not exceed 64 characters.';
    }

    if (workingDaysList.length === 0) {
      errors.workingDays = 'Select at least one working day.';
    }

    return errors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!isAdmin || saving) return;

    setSubmitError(null);
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSaving(true);
    try {
      const updated = await organizationService.updateCurrentOrganization({
        name,
        industry,
        timezone,
        workingDays: serializedDays,
      });
      setOrganization(updated);
      syncFormFromOrg(updated);
      toast.success('Organization profile has been updated.', 'Settings saved');
      // Refresh AuthContext so sidebar organizationName reflects any rename immediately.
      refreshCurrentUser().catch(() => {});
    } catch (err) {
      const parsed = parseApiError(err);
      if (Object.keys(parsed.fieldErrors).length > 0) {
        setFieldErrors(parsed.fieldErrors);
      }
      setSubmitError(parsed.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, ease: 'easeOut' }}
      className="space-y-6"
    >
      <PageHeader
        title="Organization"
        description={
          isAdmin
            ? 'Manage workspace identity, default timezone, and operating schedule.'
            : 'Workspace configuration for your TaskFlow organization.'
        }
        meta={
          <Badge tone={isAdmin ? 'cyan' : 'neutral'} dot>
            {isAdmin ? 'Administrator Access' : 'Read-Only View'}
          </Badge>
        }
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={fetchOrganization}
            disabled={loading || saving}
            leftIcon={<RefreshCw className="h-3.5 w-3.5" />}
          >
            Refresh
          </Button>
        }
      />

      {loading ? (
        <Card>
          <CardBody className="space-y-4">
            <Skeleton className="h-5 w-48" />
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          </CardBody>
        </Card>
      ) : error ? (
        <ErrorState
          title="Unable to load organization details"
          description={error.message}
          onRetry={fetchOrganization}
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Primary settings card */}
          <div className="lg:col-span-8">
            <Card>
              <CardHeader
                title="Organization Profile"
                description={
                  isAdmin
                    ? 'Updates apply across all teams, projects, and schedules in this workspace.'
                    : 'Only organization administrators can edit these settings.'
                }
              />

              {isAdmin ? (
                <form onSubmit={handleSubmit} noValidate>
                  <CardBody className="space-y-5">
                    {submitError && (
                      <div
                        role="alert"
                        className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-xs text-red-700"
                      >
                        <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" aria-hidden="true" />
                        <span>{submitError}</span>
                      </div>
                    )}

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <Input
                        label="Organization Name"
                        name="name"
                        value={name}
                        onChange={(e) => {
                          setName(e.target.value);
                          if (fieldErrors.name) setFieldErrors((p) => ({ ...p, name: undefined }));
                        }}
                        error={fieldErrors.name}
                        placeholder="e.g. Acme Engineering"
                        disabled={saving}
                        required
                      />

                      <Input
                        label="Industry"
                        name="industry"
                        value={industry}
                        onChange={(e) => {
                          setIndustry(e.target.value);
                          if (fieldErrors.industry) setFieldErrors((p) => ({ ...p, industry: undefined }));
                        }}
                        error={fieldErrors.industry}
                        placeholder="e.g. Software & Cloud Services"
                        disabled={saving}
                      />
                    </div>

                    <div className="space-y-2">
                      <Input
                        label="IANA Timezone"
                        name="timezone"
                        list="taskflow-iana-timezones"
                        value={timezone}
                        onChange={(e) => {
                          setTimezone(e.target.value);
                          if (fieldErrors.timezone) setFieldErrors((p) => ({ ...p, timezone: undefined }));
                        }}
                        error={fieldErrors.timezone}
                        helperText="Must be a valid IANA timezone identifier (for example, UTC, America/New_York, Asia/Kolkata)."
                        leftIcon={<Globe className="h-4 w-4" />}
                        disabled={saving}
                        required
                      />
                      <datalist id="taskflow-iana-timezones">
                        {COMMON_TIMEZONES.map((tz) => (
                          <option key={tz} value={tz} />
                        ))}
                      </datalist>
                    </div>

                    <div>
                      <span className="block text-xs font-medium text-ink-muted mb-2 tracking-wide">
                        Working Days <span className="text-red-600">*</span>
                      </span>
                      <div className="flex flex-wrap gap-2">
                        {WEEKDAYS.map((day) => {
                          const selected = workingDaysList.includes(day.code);
                          return (
                            <button
                              key={day.code}
                              type="button"
                              disabled={saving}
                              onClick={() => toggleDay(day.code)}
                              aria-pressed={selected}
                              className={[
                                'h-9 px-3.5 rounded-lg text-xs font-mono font-medium border transition-colors',
                                selected
                                  ? 'bg-brand-500/15 text-brand-600 border-brand-500/45'
                                  : 'bg-canvas text-ink-muted border-line hover:border-line-strong hover:text-ink',
                              ].join(' ')}
                            >
                              {day.label}
                            </button>
                          );
                        })}
                      </div>
                      {fieldErrors.workingDays ? (
                        <p role="alert" className="mt-1.5 text-xs text-red-600">
                          {fieldErrors.workingDays}
                        </p>
                      ) : (
                        <p className="mt-1.5 text-xs text-ink-faint font-mono">
                          Saved value: {serializedDays || 'None selected'}
                        </p>
                      )}
                    </div>
                  </CardBody>

                  <CardFooter>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={!isDirty || saving}
                      onClick={() => syncFormFromOrg(organization)}
                      leftIcon={<RotateCcw className="h-3.5 w-3.5" />}
                    >
                      Reset
                    </Button>
                    <Button
                      type="submit"
                      variant="primary"
                      size="sm"
                      disabled={!isDirty}
                      loading={saving}
                      leftIcon={<Save className="h-3.5 w-3.5" />}
                    >
                      Save Changes
                    </Button>
                  </CardFooter>
                </form>
              ) : (
                <CardBody>
                  <dl className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                    <div className="space-y-1">
                      <dt className="text-[11px] uppercase tracking-wider text-ink-faint font-medium">
                        Organization Name
                      </dt>
                      <dd className="text-sm font-medium text-ink">
                        {organization?.name || '—'}
                      </dd>
                    </div>

                    <div className="space-y-1">
                      <dt className="text-[11px] uppercase tracking-wider text-ink-faint font-medium">
                        Industry
                      </dt>
                      <dd className="text-sm text-ink">
                        {organization?.industry || 'Not specified'}
                      </dd>
                    </div>

                    <div className="space-y-1">
                      <dt className="text-[11px] uppercase tracking-wider text-ink-faint font-medium">
                        Timezone
                      </dt>
                      <dd className="text-sm font-mono text-ink">
                        {organization?.timezone || 'UTC'}
                      </dd>
                    </div>

                    <div className="space-y-1">
                      <dt className="text-[11px] uppercase tracking-wider text-ink-faint font-medium">
                        Working Days
                      </dt>
                      <dd className="flex flex-wrap gap-1.5 pt-0.5">
                        {parseWorkingDays(organization?.workingDays).map((code) => (
                          <Badge key={code} tone="cyan" mono>
                            {code}
                          </Badge>
                        ))}
                      </dd>
                    </div>
                  </dl>
                </CardBody>
              )}
            </Card>
          </div>

          {/* Immutable Workspace Metadata */}
          <div className="lg:col-span-4">
            <Card>
              <CardHeader
                title="Workspace Metadata"
                description="System-managed organization identifiers and audit timestamps."
              />
              <CardBody>
                <dl className="divide-y divide-line -my-2">
                  <div className="py-3 flex items-center justify-between gap-3">
                    <dt className="flex items-center gap-2 text-xs text-ink-muted">
                      <Hash className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                      <span>Organization ID</span>
                    </dt>
                    <dd className="text-xs font-mono text-ink tabular-nums">
                      #{organization?.id}
                    </dd>
                  </div>

                  <div className="py-3 flex items-center justify-between gap-3">
                    <dt className="flex items-center gap-2 text-xs text-ink-muted">
                      <Building2 className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                      <span>Slug</span>
                    </dt>
                    <dd className="text-xs font-mono text-brand-600 truncate">
                      {organization?.slug || '—'}
                    </dd>
                  </div>

                  <div className="py-3 flex items-center justify-between gap-3">
                    <dt className="flex items-center gap-2 text-xs text-ink-muted">
                      <CalendarDays className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                      <span>Created</span>
                    </dt>
                    <dd className="text-xs font-mono text-ink-muted tabular-nums">
                      {formatDateTime(organization?.createdAt)}
                    </dd>
                  </div>

                  <div className="py-3 flex items-center justify-between gap-3">
                    <dt className="flex items-center gap-2 text-xs text-ink-muted">
                      <Clock className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                      <span>Last Updated</span>
                    </dt>
                    <dd className="text-xs font-mono text-ink-muted tabular-nums">
                      {formatDateTime(organization?.updatedAt)}
                    </dd>
                  </div>
                </dl>

                <div className="mt-4 pt-3 border-t border-line flex items-start gap-2 text-[11px] text-ink-muted leading-relaxed">
                  <Shield className="h-3.5 w-3.5 text-ink-faint shrink-0 mt-0.5" aria-hidden="true" />
                  <span>
                    All changes to organization settings are automatically recorded in the backend
                    audit log.
                  </span>
                </div>
              </CardBody>
            </Card>
          </div>
        </div>
      )}

      {!loading && !error && hasRole([ROLES.ADMIN, ROLES.PROJECT_MANAGER]) && (
        <Card>
          <CardHeader
            title="Members & roles"
            description="Existing members of this organization and their organization roles."
            action={
              <Link to="/employees" className="text-xs font-semibold text-brand-700 hover:underline">
                Manage members
              </Link>
            }
          />
          <CardBody>
            {members.length === 0 ? (
              <p className="text-sm text-ink-muted">No members are available to display.</p>
            ) : (
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 xl:grid-cols-3">
                {members.map((member) => {
                  const roleMeta = ROLE_META[member.role] || { label: member.role, tone: 'neutral' };
                  return (
                    <div
                      key={member.id}
                      className="flex items-center gap-2.5 rounded-lg border border-line bg-subtle/40 p-3"
                    >
                      <Avatar name={member.fullName} color={member.avatarColor} size="sm" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-medium text-ink">
                          {member.fullName || member.email}
                        </p>
                        <p className="truncate text-[11px] text-ink-faint">{member.email}</p>
                      </div>
                      <Badge tone={roleMeta.tone} dot>
                        {roleMeta.label || member.role}
                      </Badge>
                    </div>
                  );
                })}
              </div>
            )}
          </CardBody>
        </Card>
      )}
    </motion.div>
  );
}
