import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { Provider } from 'react-redux';
import store from './store/store';
import lightTheme from './theme/lightTheme';
import AppRoutes from './routes/AppRoutes';
import { ConfirmProvider } from './components/feedback/ConfirmProvider';
import Toaster from './components/feedback/Toaster';

export default function App() {
  return (
    <Provider store={store}>
      <ThemeProvider theme={lightTheme}>
        <CssBaseline />
        <ConfirmProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
          <Toaster />
        </ConfirmProvider>
      </ThemeProvider>
    </Provider>
  );
}
