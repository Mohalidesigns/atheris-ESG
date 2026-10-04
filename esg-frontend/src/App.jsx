import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import store from './store/store';
import LanguageRoot from './i18n/LanguageRoot';
import AppRoutes from './routes/AppRoutes';
import { ConfirmProvider } from './components/feedback/ConfirmProvider';
import Toaster from './components/feedback/Toaster';

export default function App() {
  return (
    <Provider store={store}>
      <LanguageRoot>
        <ConfirmProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
          <Toaster />
        </ConfirmProvider>
      </LanguageRoot>
    </Provider>
  );
}
