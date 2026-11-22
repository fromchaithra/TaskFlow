import { useEffect, useMemo, useState } from 'react';
import { CalendarPlus, Save } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Textarea } from '../../components/common/FormControls';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { parseApiError } from '../../utils/errorHandler';
import {
  buildDefaultSprintDates,
  SPRINT_GOAL_MAX_LENGTH,
  SPRINT_NAME_MAX_LENGTH,
  validateSprintForm,
} from './sprintConstants';

function buildInitialForm({ sprint, project }) {
  if (sprint) {
    return {
      name: sprint.name || '',
      goal: sprint.goal || '',
      startDate: sprint.startDate || '',
      endDate: sprint.endDate || '',
    };
  }

  const defaults = buildDefaultSprintDates(project);
  return {
    name: '',
    goal: '',
    startDate: defaults.startDate,
    endDate: defaults.endDate,
  };
}

/**
 * Create/edit sprint modal. Validation mirrors CreateSprintRequest/UpdateSprintRequest
 * constraints and SprintServiceImpl.validateSprintDates().
 */
export function SprintFormModal({ isOpen, onClose, onSubmit, sprint = null, project = null }) {
  const isEdit = Boolean(sprint);
  const [form, setForm] = useState(() => buildInitialForm({ sprint, project }));
  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setForm(buildInitialForm({ sprint, project }));
    setFieldErrors({});
    setGeneralError(null);
    setSubmitting(false);
  }, [isOpen, sprint, project]);

  const minStartDate = project?.startDate || undefined;
  const maxEndDate = project?.endDate || undefined;

  const goalLength = form.goal?.length || 0;

  const dateHelper = useMemo(() => {
    const parts = [];
    if (project?.startDate) parts.push(`Project starts ${project.startDate}`);
    if (project?.endDate) parts.push(`ends ${project.endDate}`);
    return parts.length ? `${parts.join(' · ')}.` : 'End date must be after the start date.';
  }, [project?.startDate, project?.endDate]);

  const updateField = (key) => (event) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, [key]: value }));
    setFieldErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
    setGeneralError(null);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    const clientErrors = validateSprintForm(form, project);
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setGeneralError(null);

    try {
      await onSubmit?.({
        name: form.name.trim(),
        goal: form.goal.trim(),
        startDate: form.startDate,
        endDate: form.endDate,
      });
      onClose?.();
    } catch (err) {
      const parsed = parseApiError(err);
      const nextErrors = {};
      for (const [field, message] of Object.entries(parsed.fieldErrors || {})) {
        nextErrors[field] = message;
      }
      setFieldErrors(nextErrors);
      setGeneralError(parsed.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={submitting ? undefined : onClose}
      closeOnBackdrop={!submitting}
      size="lg"
      title={isEdit ? 'Edit sprint' : 'Create sprint'}
      description={
        isEdit
          ? 'Update the sprint name, goal, or delivery window. The backend rejects overlapping active sprints.'
          : 'Plan a delivery window. Only one sprint can be active per project at a time.'
      }
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="sprint-form"
            loading={submitting}
            leftIcon={
              isEdit ? <Save className="h-4 w-4" /> : <CalendarPlus className="h-4 w-4" />
            }
          >
            {isEdit ? 'Save changes' : 'Create sprint'}
          </Button>
        </>
      }
    >
      <form id="sprint-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
        {generalError && (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-xs text-red-600"
          >
            {generalError}
          </div>
        )}

        <Input
          label="Sprint name"
          required
          value={form.name}
          onChange={updateField('name')}
          error={fieldErrors.name}
          placeholder="e.g. Sprint 12 — Checkout hardening"
          maxLength={SPRINT_NAME_MAX_LENGTH}
          autoFocus
        />

        <Textarea
          label="Sprint goal"
          value={form.goal}
          onChange={updateField('goal')}
          error={fieldErrors.goal}
          helperText={`${goalLength}/${SPRINT_GOAL_MAX_LENGTH} characters. Optional.`}
          placeholder="What outcome should this sprint deliver?"
          rows={4}
          maxLength={SPRINT_GOAL_MAX_LENGTH}
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Input
            label="Start date"
            type="date"
            required
            value={form.startDate}
            onChange={updateField('startDate')}
            error={fieldErrors.startDate}
            min={minStartDate}
            max={form.endDate || maxEndDate}
          />
          <Input
            label="End date"
            type="date"
            required
            value={form.endDate}
            onChange={updateField('endDate')}
            error={fieldErrors.endDate}
            min={form.startDate || minStartDate}
            max={maxEndDate}
            helperText={dateHelper}
          />
        </div>
      </form>
    </Modal>
  );
}
