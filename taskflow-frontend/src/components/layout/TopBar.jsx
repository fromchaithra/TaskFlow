import { useEffect, useRef, useState } from 'react';
import {
  Bell,
  ChevronDown,
  LogOut,
  Menu,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Sun,
  User,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import { useTheme } from '../../context/ThemeContext';
import { ROLE_META } from '../../utils/constants';
import { getInitials, sanitizeAvatarColor } from '../../utils/formatters';
import { notificationService } from '../../services/dashboardService';
import { Badge } from '../common/Badge';
import { Breadcrumbs } from './Breadcrumbs';

export function TopBar({
  sidebarCollapsed = false,
  onToggleCollapse,
  onOpenMobileMenu,
  onOpenProfile,
  onRequestLogout,
}) {
  const { user } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [loadingNotifications, setLoadingNotifications] = useState(false);

  const profileRef = useRef(null);
  const notifRef = useRef(null);

  useEffect(() => {
    function handleOutsideClick(event) {
      if (profileRef.current && !profileRef.current.contains(event.target)) {
        setProfileMenuOpen(false);
      }
      if (notifRef.current && !notifRef.current.contains(event.target)) {
        setNotificationsOpen(false);
      }
    }
    function handleEsc(event) {
      if (event.key === 'Escape') {
        setProfileMenuOpen(false);
        setNotificationsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleOutsideClick);
    document.addEventListener('keydown', handleEsc);
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('keydown', handleEsc);
    };
  }, []);

  useEffect(() => {
    let active = true;
    async function loadCount() {
      try {
        const count = await notificationService.unreadCount();
        if (active) setUnreadCount(Number(count) || 0);
      } catch {
        // ignore
      }
    }
    loadCount();
    const interval = setInterval(loadCount, 60000);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (!notificationsOpen) return;
    let active = true;
    async function load() {
      setLoadingNotifications(true);
      try {
        const data = await notificationService.list({ size: 10 });
        if (active) setNotifications(data?.content || []);
      } catch {
        if (active) setNotifications([]);
      } finally {
        if (active) setLoadingNotifications(false);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [notificationsOpen]);

  const roleInfo = ROLE_META[user?.role] || {
    label: user?.role || 'Member',
    shortLabel: 'Member',
    tone: 'neutral',
  };
  const avatarBg = sanitizeAvatarColor(user?.avatarColor);

  return (
    <header className="sticky top-0 z-30 h-[68px] bg-canvas/85 backdrop-blur-md border-b border-line px-4 lg:px-7 flex items-center justify-between gap-4">
      <div className="flex items-center gap-2 min-w-0">
        <button
          type="button"
          onClick={onOpenMobileMenu}
          aria-label="Open navigation menu"
          className="lg:hidden p-1.5 rounded-lg text-ink-muted hover:text-ink hover:bg-subtle transition-colors shrink-0"
        >
          <Menu className="h-[18px] w-[18px]" aria-hidden="true" />
        </button>

        <button
          type="button"
          onClick={onToggleCollapse}
          aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className="hidden lg:flex h-9 w-9 items-center justify-center rounded-lg text-ink-faint hover:text-ink hover:bg-subtle transition-colors shrink-0"
        >
          {sidebarCollapsed ? (
            <PanelLeftOpen className="h-[18px] w-[18px]" aria-hidden="true" />
          ) : (
            <PanelLeftClose className="h-[18px] w-[18px]" aria-hidden="true" />
          )}
        </button>

        <span className="hidden lg:block h-5 w-px bg-line shrink-0" aria-hidden="true" />

        <Breadcrumbs />
      </div>

      <div className="flex items-center gap-1.5 shrink-0">
        <button
          type="button"
          onClick={toggleTheme}
          title={isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
          aria-label={isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
          aria-pressed={isDark}
          className="h-9 w-9 rounded-lg flex items-center justify-center text-ink-muted hover:text-ink hover:bg-subtle transition-colors"
        >
          {isDark ? (
            <Sun className="h-4 w-4" aria-hidden="true" />
          ) : (
            <Moon className="h-4 w-4" aria-hidden="true" />
          )}
        </button>

        <div ref={notifRef} className="relative">
          <button
            type="button"
            onClick={() => {
              setNotificationsOpen((prev) => !prev);
              setProfileMenuOpen(false);
            }}
            aria-label="Notifications"
            aria-expanded={notificationsOpen}
            className="relative h-9 w-9 rounded-lg flex items-center justify-center text-ink-muted hover:text-ink hover:bg-subtle transition-colors"
          >
            <Bell className="h-4 w-4" aria-hidden="true" />
            {unreadCount > 0 && (
              <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-red-500 ring-2 ring-canvas" />
            )}
          </button>

          {notificationsOpen && (
            <div className="absolute right-0 mt-2 w-80 rounded-xl bg-surface border border-line shadow-overlay p-0 overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 border-b border-line bg-subtle/60">
                <p className="text-xs font-semibold text-ink">Notifications</p>
                {unreadCount > 0 && (
                  <button
                    type="button"
                    onClick={async () => {
                      try {
                        await notificationService.markAllRead();
                        setUnreadCount(0);
                        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
                      } catch {
                        // ignore
                      }
                    }}
                    className="text-[10px] font-medium text-brand-600 hover:text-brand-700"
                  >
                    Mark all read
                  </button>
                )}
              </div>
              <div className="max-h-80 overflow-y-auto">
                {loadingNotifications ? (
                  <p className="px-4 py-6 text-xs text-ink-muted text-center">Loading…</p>
                ) : notifications.length === 0 ? (
                  <p className="px-4 py-6 text-xs text-ink-muted text-center">No notifications yet.</p>
                ) : (
                  <ul className="divide-y divide-line">
                    {notifications.map((n) => (
                      <li key={n.id}>
                        <button
                          type="button"
                          onClick={async () => {
                            if (!n.read) {
                              try {
                                await notificationService.markRead(n.id);
                                setUnreadCount((c) => Math.max(0, c - 1));
                                setNotifications((prev) =>
                                  prev.map((x) => (x.id === n.id ? { ...x, read: true } : x))
                                );
                              } catch {
                                // ignore
                              }
                            }
                          }}
                          className={[
                            'w-full text-left px-4 py-3 hover:bg-subtle transition-colors',
                            n.read ? 'opacity-70' : '',
                          ].join(' ')}
                        >
                          <p className="text-xs font-semibold text-ink">{n.title}</p>
                          <p className="text-[11px] text-ink-muted mt-0.5 leading-relaxed">{n.message}</p>
                          <p className="text-[10px] text-ink-faint mt-1">{new Date(n.createdAt).toLocaleString()}</p>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="h-5 w-px bg-line mx-0.5" aria-hidden="true" />

        <div ref={profileRef} className="relative">
          <button
            type="button"
            onClick={() => {
              setProfileMenuOpen((prev) => !prev);
              setNotificationsOpen(false);
            }}
            aria-expanded={profileMenuOpen}
            aria-haspopup="menu"
            className="flex items-center gap-2.5 pl-1.5 pr-2.5 py-1 rounded-lg hover:bg-subtle transition-colors"
          >
            <span
              className="h-8 w-8 rounded-lg flex items-center justify-center text-[11px] font-semibold text-white shrink-0 ring-1 ring-inset ring-line"
              style={{ backgroundColor: avatarBg }}
              aria-hidden="true"
            >
              {getInitials(user?.fullName)}
            </span>
            <div className="hidden sm:flex flex-col items-start text-left max-w-[150px]">
              <span className="text-xs font-semibold text-ink truncate w-full leading-tight">{user?.fullName || 'Workspace Member'}</span>
              <span className="text-[11px] text-ink-faint truncate w-full leading-tight mt-0.5">{roleInfo.shortLabel}</span>
            </div>
            <ChevronDown
              className={`h-3.5 w-3.5 text-ink-faint transition-transform duration-150 ${profileMenuOpen ? 'rotate-180' : ''}`}
              aria-hidden="true"
            />
          </button>

          {profileMenuOpen && (
            <div role="menu" className="absolute right-0 mt-2 w-64 rounded-xl bg-surface border border-line shadow-overlay overflow-hidden">
              <div className="px-4 py-3 border-b border-line bg-subtle/60">
                <p className="text-xs font-semibold text-ink truncate">{user?.fullName}</p>
                <p className="text-[11px] text-ink-muted truncate mt-0.5">{user?.email}</p>
                <div className="mt-2 flex items-center gap-2 flex-wrap">
                  <Badge tone={roleInfo.tone}>{roleInfo.label}</Badge>
                  {user?.employeeCode && <span className="text-[10px] font-mono text-ink-faint">{user.employeeCode}</span>}
                </div>
              </div>
              <div className="p-1.5">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setProfileMenuOpen(false);
                    onOpenProfile();
                  }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs text-ink hover:bg-subtle transition-colors"
                >
                  <User className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />
                  <span>Account Profile</span>
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setProfileMenuOpen(false);
                    onRequestLogout();
                  }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs text-red-600 hover:bg-red-50 transition-colors"
                >
                  <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>Sign Out</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
