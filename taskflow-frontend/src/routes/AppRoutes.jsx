import { Route, Routes } from 'react-router-dom';
import { ALL_NAV_ITEMS, ROLES } from '../utils/constants';
import { AppLayout } from '../components/layout/AppLayout';
import { LandingPage } from '../features/landing/LandingPage';
import { WelcomePage } from '../features/welcome/WelcomePage';
import { LoginPage } from '../features/auth/LoginPage';
import { FirstAdminRegistrationPage } from '../features/auth/FirstAdminRegistrationPage';
import { ActivateAccountPage } from '../features/auth/ActivateAccountPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { OrganizationPage } from '../features/organization/OrganizationPage';
import { UsersPage } from '../features/users/UsersPage';
import { TeamsPage } from '../features/teams/TeamsPage';
import { TeamDetailPage } from '../features/teams/TeamDetailPage';
import { ProjectsPage } from '../features/projects/ProjectsPage';
import { ProjectDetailPage } from '../features/projects/ProjectDetailPage';
import { TasksPage } from '../features/tasks/TasksPage';
import { TaskDetailPage } from '../features/tasks/TaskDetailPage';
import { TaskBoardPage } from '../features/tasks/TaskBoardPage';
import { SprintsPage } from '../features/sprints/SprintsPage';
import { BugListPage } from '../features/bugs/BugListPage';
import { BugDetailPage } from '../features/bugs/BugDetailPage';
import { ReportsPage } from '../features/reports/ReportsPage';
import { TestCaseListPage } from '../features/qa/TestCaseListPage';
import { TestCaseDetailPage } from '../features/qa/TestCaseDetailPage';
import { TestSuiteListPage } from '../features/qa/TestSuiteListPage';
import { TestSuiteDetailPage } from '../features/qa/TestSuiteDetailPage';
import { TestRunListPage } from '../features/qa/TestRunListPage';
import { TestRunDetailPage } from '../features/qa/TestRunDetailPage';
import { UnauthorizedPage } from '../features/errors/UnauthorizedPage';
import { NotFoundPage } from '../features/errors/NotFoundPage';
import { ProtectedRoute } from './ProtectedRoute';
import { RoleRoute } from './RoleRoute';
import { ModulePlaceholderPage } from '../features/placeholders/ModulePlaceholderPage';

const UPCOMING_MODULE_ROUTES = ALL_NAV_ITEMS.filter(
  (item) => item.phaseStatus === 'upcoming'
);

export function AppRoutes() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/" element={<WelcomePage />} />
      <Route path="/home" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<FirstAdminRegistrationPage />} />
      <Route path="/activate" element={<ActivateAccountPage />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />

      {/* Authenticated application shell */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          {/* Phase 2: Organization, Users & Teams */}
          <Route path="/organization" element={<OrganizationPage />} />
          <Route path="/teams" element={<TeamsPage />} />
          <Route path="/teams/:id" element={<TeamDetailPage />} />

          {/* Phase 3: Project Management */}
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/projects/:id" element={<ProjectDetailPage />} />

          {/* Phase 5: Bug Tracking */}
          <Route path="/bugs" element={<BugListPage />} />
          <Route path="/bugs/:id" element={<BugDetailPage />} />

          {/* Reports & Analytics */}
          <Route path="/reports" element={<ReportsPage />} />

          {/* Phase 6: QA Test Management */}
          <Route path="/qa/test-cases" element={<TestCaseListPage />} />
          <Route path="/qa/test-cases/:id" element={<TestCaseDetailPage />} />
          <Route path="/qa/test-suites" element={<TestSuiteListPage />} />
          <Route path="/qa/test-suites/:id" element={<TestSuiteDetailPage />} />
          <Route path="/qa/test-runs" element={<TestRunListPage />} />
          <Route path="/qa/test-runs/:id" element={<TestRunDetailPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/tasks/:id" element={<TaskDetailPage />} />
          <Route path="/kanban" element={<TaskBoardPage />} />

          {/* Phase 5: Sprint Management */}
          <Route path="/sprints" element={<SprintsPage />} />

          <Route element={<RoleRoute allowedRoles={[ROLES.ADMIN, ROLES.PROJECT_MANAGER]} />}>
            <Route path="/employees" element={<UsersPage />} />
          </Route>

          {/* Role-guarded upcoming module placeholders */}
          {UPCOMING_MODULE_ROUTES.map((moduleItem) => (
            <Route
              key={moduleItem.id}
              element={<RoleRoute allowedRoles={moduleItem.roles} />}
            >
              <Route
                path={moduleItem.path}
                element={<ModulePlaceholderPage moduleConfig={moduleItem} />}
              />
            </Route>
          ))}
        </Route>
      </Route>

      {/* Fallback 404 */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
