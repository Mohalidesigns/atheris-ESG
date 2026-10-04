import { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Button, ListItemText, Menu, MenuItem } from '@mui/material';
import { Translate } from '@mui/icons-material';
import { LANGUAGES, currentLanguage, setLanguage, tr } from '../../i18n';
import STATUS from '../../i18n/locales/status.json';
import { useSetMyLanguageMutation } from '../../features/auth/authApi';
import { toastShown } from '../../store/uiSlice';
import { userUpdated } from '../../features/auth/slices/authSlice';

const label = (code) => {
  const s = STATUS[code];
  return s?.status === 'draft' ? `${tr('Draft translation')} · ${Math.round((s.coverage ?? 0) * 100)}%` : null;
};

/**
 * WP-45 (D45): choose the interface language — on the sign-in page and in the account menu. Signed in, the choice is
 * kept on the account for every device. Languages not yet reviewed by a professional translator say so.
 */
export default function LanguagePicker({ variant = 'button', onChosen }) {
  const dispatch = useDispatch();
  const signedIn = useSelector((s) => s.auth.status === 'authenticated');
  const [save] = useSetMyLanguageMutation();
  const [anchor, setAnchor] = useState(null);
  const current = currentLanguage();
  const choose = async (code) => {
    setAnchor(null);
    onChosen?.();
    if (code === current) return;
    if (signedIn) {
      // Account state first: switching remounts the layout, whose account-language hook must not switch back.
      dispatch(userUpdated({ preferredLanguage: code }));
      save(code);
    }
    await setLanguage(code);
    if (STATUS[code]?.status === 'draft') {
      dispatch(toastShown({ severity: 'info', message: tr('This translation is a draft and has not been reviewed by a professional translator yet.') }));
    }
  };
  return (
    <>
      <Button size="small" startIcon={<Translate />} onClick={(e) => setAnchor(e.currentTarget)} aria-haspopup="menu"
        aria-label={`${tr('Language')}: ${LANGUAGES[current].name}`} sx={variant === 'menu' ? { justifyContent: 'flex-start', px: 2, width: '100%' } : undefined}>
        {LANGUAGES[current].name}
      </Button>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        {Object.entries(LANGUAGES).map(([code, l]) => (
          <MenuItem key={code} selected={code === current} onClick={() => choose(code)} lang={code}>
            <ListItemText primary={l.name} secondary={label(code)} />
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
