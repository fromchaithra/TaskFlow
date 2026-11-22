import { BrowserRouter } from 'react-router-dom';
import { MotionConfig } from 'framer-motion';
import { AuthProvider } from '../context/AuthContext';
import { ThemeProvider } from '../context/ThemeContext';
import { ToastProvider } from '../context/ToastContext';
import { ToastContainer } from '../components/common/ToastContainer';
import { AppRoutes } from '../routes/AppRoutes';

export function App() {
  return (
    <MotionConfig reducedMotion="user">
      <BrowserRouter>
        <ThemeProvider>
          <ToastProvider>
            <AuthProvider>
              <AppRoutes />
              <ToastContainer />
            </AuthProvider>
          </ToastProvider>
        </ThemeProvider>
      </BrowserRouter>
    </MotionConfig>
  );
}

export default App;
