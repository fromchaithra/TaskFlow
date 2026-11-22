import { useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Bug,
  Building2,
  CheckSquare,
  Columns3,
  FileBarChart,
  FolderKanban,
  IterationCw,
  LayoutDashboard,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  UserCog,
  UsersRound,
  X,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { NAVIGATION_SECTIONS, ROLE_META } from '../../utils/constants';
import { getInitials, sanitizeAvatarColor } from '../../utils/formatters';
import { BrandMark } from '../common/BrandMark';

const ICON_MAP = {
  LayoutDashboard,
  FolderKanban,
  CheckSquare,
  Columns3,
  IterationCw,
  Bug,
  FileBarChart,
  UsersRound,
  UserCog,
  Building2,
};

function SidebarContent({
  collapsed,
  onNavigate,
  onToggleCollapse,
  onRequestLogout,
  isMobile = false,
  onCloseMobile,
}) {
  const { user, hasRole } = useAuth();
  const roleInfo = ROLE_META[user?.role] || { label: user?.role || 'Member', tone: 'neutral' };
  const avatarBg = sanitizeAvatarColor(user?.avatarColor);
  const compact = collapsed && !isMobile;

  const visibleSections = NAVIGATION_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => hasRole(item.roles)),
  })).filter((section) => section.items.length > 0);

  return (
    <div className="flex flex-col h-full bg-surface border-r border-line select-none">
      {/* Brand header */}
      <div
        className={[
          'h-[68px] px-4 border-b border-line flex items-center shrink-0',
          compact ? 'justify-center' : 'justify-between gap-2',
        ].join(' ')}
      >
        <div className="flex items-center gap-2.5 min-w-0">
          <span className="grid place-items-center rounded-lg p-0.5 bg-brand-50 ring-1 ring-brand-100">
            <BrandMark size={26} />
          </span>
          {(!compact || isMobile) && (
            <div className="min-w-0">
              <p className="text-[15px] font-semibold tracking-tight text-ink leading-none">
                TaskFlow
              </p>
              <p className="text-[11px] text-ink-faint truncate mt-1.5 leading-none">
                {user?.organizationName || 'Enterprise Workspace'}
              </p>
            </div>
          )}
        </div>

        {isMobile ? (
          <button
            type="button"
            onClick={onCloseMobile}
            aria-label="Close navigation menu"
            title="Close navigation menu"
            className="p-1.5 rounded-lg text-ink-faint hover:text-ink hover:bg-subtle transition-colors"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        ) : !compact ? (
          <button
            type="button"
            onClick={onToggleCollapse}
            aria-label="Collapse sidebar"
            title="Collapse sidebar"
            className="p-1.5 rounded-lg text-ink-faint hover:text-ink hover:bg-subtle transition-colors shrink-0"
          >
            <PanelLeftClose className="h-4 w-4" aria-hidden="true" />
          </button>
        ) : null}
      </div>

      {/* Navigation sections */}
      <nav
        aria-label="Primary navigation"
        className={[
          'flex-1 overflow-y-auto py-5 space-y-6',
          compact ? 'px-2' : 'px-3',
        ].join(' ')}
      >
        {visibleSections.map((section) => (
          <div key={section.id}>
            {(!compact || isMobile) && (
              <p className="px-3 mb-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-ink-faint">
                {section.label}
              </p>
            )}
            {compact && <div className="mx-auto mb-2 h-px w-6 bg-line" aria-hidden="true" />}
            <ul className="space-y-1">
              {section.items.map((item) => {
                const Icon = ICON_MAP[item.icon] || LayoutDashboard;
                const isUpcoming = item.phaseStatus === 'upcoming';

                return (
                  <li key={item.id}>
                    <NavLink
                      to={item.path}
                      onClick={onNavigate}
                      title={compact ? item.label : undefined}
                      className={({ isActive }) =>
                        [
                          'group relative flex items-center rounded-lg transition-colors duration-150',
                          compact
                            ? 'justify-center h-10 w-10 mx-auto'
                            : 'gap-3 px-3 py-2.5 text-[13px]',
                          isActive
                            ? 'bg-brand-50 text-brand-700 font-semibold'
                            : 'text-ink-muted hover:text-ink hover:bg-subtle',
                        ].join(' ')
                      }
                    >
                      {({ isActive }) => (
                        <>
                          {isActive && !compact && (
                            <span
                              className="absolute left-0 top-2 bottom-2 w-[3px] rounded-r bg-brand-500"
                              aria-hidden="true"
                            />
                          )}
                          <Icon
                            className={[
                              'h-[18px] w-[18px] shrink-0 transition-colors',
                              isActive
                                ? 'text-brand-600'
                                : 'text-ink-faint group-hover:text-ink-muted',
                            ].join(' ')}
                            aria-hidden="true"
                          />
                          {(!compact || isMobile) && (
                            <>
                              <span className="truncate flex-1">{item.label}</span>
                              {isUpcoming && (
                                <span className="text-[10px] font-mono text-ink-faint shrink-0">
                                  {item.plannedPhase}
                                </span>
                              )}
                            </>
                          )}
                        </>
                      )}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      {/* User profile / logout */}
      <div className="p-3 border-t border-line shrink-0">
        {compact ? (
          <div className="flex flex-col items-center gap-2">
            <button
              type="button"
              onClick={onToggleCollapse}
              aria-label="Expand sidebar"
              title="Expand sidebar"
              className="h-10 w-10 flex items-center justify-center rounded-lg text-ink-faint hover:text-ink hover:bg-subtle transition-colors"
            >
              <PanelLeftOpen className="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={onRequestLogout}
              aria-label="Sign Out"
              title="Sign Out"
              className="h-10 w-10 flex items-center justify-center rounded-lg text-ink-faint hover:text-ink hover:bg-subtle transition-colors"
            >
              <LogOut className="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-2.5 rounded-lg px-2 py-2 bg-subtle/70">
            <span
              className="h-8 w-8 rounded-lg flex items-center justify-center text-[11px] font-semibold text-white shrink-0 ring-1 ring-inset ring-white/10"
              style={{ backgroundColor: avatarBg }}
              aria-hidden="true"
            >
              {getInitials(user?.fullName)}
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-ink truncate leading-tight">
                {user?.fullName || 'Workspace Member'}
              </p>
              <p className="text-[11px] text-ink-faint truncate leading-tight mt-0.5">
                {roleInfo.label}
              </p>
            </div>
            <button
              type="button"
              onClick={onRequestLogout}
              aria-label="Sign Out"
              title="Sign Out"
              className="p-1.5 rounded-lg text-ink-faint hover:text-ink hover:bg-surface transition-colors shrink-0"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export function Sidebar({
  collapsed,
  onToggleCollapse,
  onRequestLogout,
  mobileOpen,
  onCloseMobile,
}) {
  useEffect(() => {
    if (!mobileOpen) return undefined;
    function handleEsc(e) {
      if (e.key === 'Escape') onCloseMobile();
    }
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [mobileOpen, onCloseMobile]);

  return (
    <>
      {/* Desktop persistent sidebar */}
      <aside
        className={[
          'hidden lg:block shrink-0 h-screen sticky top-0 transition-[width] duration-200 ease-out',
          collapsed ? 'w-[76px]' : 'w-[264px]',
        ].join(' ')}
      >
        <SidebarContent
          collapsed={collapsed}
          onToggleCollapse={onToggleCollapse}
          onRequestLogout={onRequestLogout}
        />
      </aside>

      {/* Mobile & tablet slide-over drawer */}
      <AnimatePresence>
        {mobileOpen && (
          <div className="fixed inset-0 z-40 lg:hidden flex">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              onClick={onCloseMobile}
              className="fixed inset-0 bg-ink/40 backdrop-blur-[2px]"
            />
            <motion.aside
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              className="relative z-10 w-[280px] max-w-[85vw] h-full shadow-overlay"
            >
              <SidebarContent
                collapsed={false}
                isMobile
                onNavigate={onCloseMobile}
                onCloseMobile={onCloseMobile}
                onRequestLogout={onRequestLogout}
              />
            </motion.aside>
          </div>
        )}
      </AnimatePresence>
    </>
  );
}
