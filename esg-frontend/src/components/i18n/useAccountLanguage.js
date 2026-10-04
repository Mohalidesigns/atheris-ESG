import { useEffect } from 'react';
import { useSelector } from 'react-redux';
import { LANGUAGES, currentLanguage, setLanguage } from '../../i18n';
import { useSetMyLanguageMutation } from '../../features/auth/authApi';

/** Applies the account's language when a session starts; a first choice made on this device before signing in is saved to the account. */
export default function useAccountLanguage() {
  const preferred = useSelector((s) => s.auth.user?.preferredLanguage);
  const signedIn = useSelector((s) => s.auth.status === 'authenticated');
  const [save] = useSetMyLanguageMutation();
  useEffect(() => {
    if (!signedIn) return;
    const account = LANGUAGES[preferred] ? preferred : 'en';
    const device = currentLanguage();
    if (account === device) return;
    if (account === 'en') save(device);
    else setLanguage(account);
  }, [preferred, signedIn, save]);
}
