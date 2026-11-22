import { useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../../hooks/useToast';
import { ConfirmDialog } from '../common/ConfirmDialog';
import { ProfileModal } from '../../features/auth/ProfileModal';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';

const SIDEBAR_PREF_KEY = 'taskflow_sidebar_collapsed';

function readSidebarPreference() {
  try {
    return window.localStorage.getItem(SIDEBAR_PREF_KEY) === 'true';
  } catch {
    return false;
  }
}

export function AppLayout() {
  const { logout } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [sidebarCollapsed, setSidebarCollapsed] = useState(readSidebarPreference);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const handleToggleCollapse = () => {
    setSidebarCollapsed((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(SIDEBAR_PREF_KEY, String(next));
      } catch {
        // Ignore storage errors in restricted browsing contexts.
      }
      return next;
    });
  };

  const handleConfirmLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
      toast.info('You have been signed out cleanly.', 'Signed out');
      navigate('/login', { replace: true });
    } finally {
      setLoggingOut(false);
      setLogoutConfirmOpen(false);
    }
  };

  return (
    <div className="min-h-screen bg-subtle text-ink flex">
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggleCollapse={handleToggleCollapse}
          onRequestLogout={() => setLogoutConfirmOpen(true)}
        mobileOpen={mobileMenuOpen}
        onCloseMobile={() => setMobileMenuOpen(false)}
      />

      <div className="flex-1 min-w-0 flex flex-col min-h-screen">
        <TopBar
          sidebarCollapsed={sidebarCollapsed}
          onToggleCollapse={handleToggleCollapse}
          onOpenMobileMenu={() => setMobileMenuOpen(true)}
          onOpenProfile={() => setProfileOpen(true)}
          onRequestLogout={() => setLogoutConfirmOpen(true)}
        />

        <main className="flex-1 w-full max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8 py-6 lg:py-8">
          <Outlet context={{ openProfile: () => setProfileOpen(true) }} />
        </main>
      </div>

      <ProfileModal isOpen={profileOpen} onClose={() => setProfileOpen(false)} />

      <ConfirmDialog
        isOpen={logoutConfirmOpen}
        onClose={() => setLogoutConfirmOpen(false)}
        onConfirm={handleConfirmLogout}
        title="Sign out of TaskFlow?"
        message="Your current access session will be revoked and you will need to authenticate again to continue."
        confirmLabel="Sign Out"
        cancelLabel="Stay Signed In"
        variant="danger"
        loading={loggingOut}
      />
    </div>
  );
}
