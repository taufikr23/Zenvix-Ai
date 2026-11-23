import { createContext, useContext, useEffect, useState } from 'react';
import { request, setAccessToken } from './api';
const Context = createContext(null);
const KEY = 'documind.session';
function readSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(KEY));
    if (value?.token && value.expiresAt > Date.now()) return value;
    sessionStorage.removeItem(KEY);
  } catch { /* Storage mungkin dibatasi browser. */ }
  return null;
}
export function SessionProvider({ children }) {
  const [session, setSession] = useState(readSession);
  const [checking, setChecking] = useState(true);
  const [notice, setNotice] = useState('');
  function logout(message = '') {
    setAccessToken(null); setSession(null); setNotice(message);
    try { sessionStorage.removeItem(KEY); } catch { /* Sesi memori tetap dibersihkan. */ }
  }
  function login(result) {
    const value = { token: result.accessToken, user: result.user, expiresAt: Date.now() + result.expiresIn * 1000 };
    setAccessToken(value.token); setSession(value); setNotice('');
    try { sessionStorage.setItem(KEY, JSON.stringify(value)); } catch { /* Login tetap bekerja dalam memori. */ }
  }
  useEffect(() => {
    const expire = () => logout('Sesi Anda berakhir. Masuk kembali untuk melanjutkan.');
    window.addEventListener('session-expired', expire);
    return () => window.removeEventListener('session-expired', expire);
  }, []);
  useEffect(() => {
    if (!session) { setChecking(false); return; }
    setAccessToken(session.token);
    const controller = new AbortController();
    let active = true;
    request('/auth/me', { signal: controller.signal }).then(user => {
      if (active) setSession(current => current ? { ...current, user } : null);
    }).catch(error => {
      if (active) logout(error.status === 401 ? 'Sesi Anda berakhir. Silakan masuk kembali.' : 'Tidak dapat memverifikasi sesi. Silakan masuk kembali.');
    }).finally(() => { if (active) setChecking(false); });
    const timer = setTimeout(() => logout('Sesi Anda berakhir. Masuk kembali untuk melanjutkan.'), Math.max(0, session.expiresAt - Date.now()));
    return () => { active = false; controller.abort(); clearTimeout(timer); };
    // Token yang sama tidak perlu diverifikasi ulang saat profil diperbarui.
  }, [session?.token]);
  return <Context.Provider value={{ session, login, logout, notice, checking }}>{children}</Context.Provider>;
}
export function useSession() { return useContext(Context); }
