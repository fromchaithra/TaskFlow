import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Layers } from 'lucide-react';
import { ROLE_META } from '../../utils/constants';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card, CardBody } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';

export function ModulePlaceholderPage({ moduleConfig }) {
  const {
    label = 'Workspace Module',
    description = 'This module is scheduled for an upcoming frontend phase.',
    plannedPhase = 'Upcoming',
    backendEndpoint,
    roles = [],
  } = moduleConfig || {};

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.16, ease: 'easeOut' }}
      className="space-y-6"
    >
      <PageHeader
        title={label}
        description={description}
        meta={
          <Badge tone="cyan" mono>
            {plannedPhase}
          </Badge>
        }
        actions={
          <Link to="/dashboard">
            <Button
              variant="secondary"
              size="sm"
              leftIcon={<ArrowLeft className="h-3.5 w-3.5" />}
            >
              Back to Dashboard
            </Button>
          </Link>
        }
      />

      <Card>
        <CardBody className="py-12 px-6 flex flex-col items-center text-center">
          <div className="h-12 w-12 rounded-xl bg-canvas border border-line flex items-center justify-center text-brand-600 mb-4">
            <Layers className="h-5 w-5" aria-hidden="true" />
          </div>

          <h2 className="text-base font-semibold text-ink">
            {label} UI is scheduled for {plannedPhase}
          </h2>
          <p className="text-xs text-ink-muted mt-2 max-w-lg leading-relaxed">
            Route protection and role-based navigation are active for this workspace section.
            Interactive screens and data tables will connect directly to the existing Spring Boot
            REST endpoints in {plannedPhase}.
          </p>

          <div className="mt-6 w-full max-w-md rounded-lg border border-line bg-subtle/60 p-4 text-left space-y-3">
            {backendEndpoint && (
              <div className="flex items-center justify-between gap-3">
                <span className="text-[11px] text-ink-faint">Backend API</span>
                <code className="text-[11px] font-mono text-brand-600 truncate">
                  {backendEndpoint}
                </code>
              </div>
            )}

            <div className="flex items-start justify-between gap-3 pt-2 border-t border-line">
              <span className="text-[11px] text-ink-faint shrink-0">Permitted Roles</span>
              <div className="flex flex-wrap justify-end gap-1.5">
                {roles.map((role) => {
                  const info = ROLE_META[role];
                  return (
                    <Badge key={role} tone={info?.tone || 'neutral'}>
                      {info?.label || role}
                    </Badge>
                  );
                })}
              </div>
            </div>
          </div>
        </CardBody>
      </Card>
    </motion.div>
  );
}
