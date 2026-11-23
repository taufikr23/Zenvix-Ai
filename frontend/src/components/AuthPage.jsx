import { useState } from 'react';
import { ArrowRight, Check, Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { Brand, ErrorNotice, Spinner } from './UI';
import { PlanetScene } from './PlanetScene';
import { request } from '../lib/api';
import { useSession } from '../lib/session';
export function AuthPage() {
  const { login, notice } = useSession();
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setError('');
    if (new TextEncoder().encode(password).length > 72) { setError('Password maksimal 72 byte. Kurangi jumlah karakter.'); return; }
    setBusy(true);
    try {
      if (mode === 'register') await request('/auth/register', { method: 'POST', body: { email, password }, publicRequest: true });
      const result = await request('/auth/login', { method: 'POST', body: { email, password }, publicRequest: true });
      login(result);
    } catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  return <main className="auth-layout">
    <section className="auth-story"><Brand/><div className="auth-story-main"><span className="eyebrow light">RUANG UNTUK IDE BESAR</span><h1>Rasa ingin tahu Anda.<br/><em>Zenvix kemungkinan.</em></h1><p>Temukan perspektif baru bersama Zenvix. Tulis, pelajari, ciptakan—mulai dari satu percakapan.</p>
      <div className="zenvix-orbit"><PlanetScene/><span className="orbit-caption">A little curiosity. Endless possibilities.</span></div>
      <div className="auth-benefits"><span><Check size={16}/> Berbagai topik</span><span><Check size={16}/> Riwayat pribadi</span></div></div><div className="auth-foot">ASISTEN AI PRIBADI <span>Zenvix</span></div></section>
    <section className="auth-form-wrap"><div className="mobile-brand"><Brand/></div><div className="auth-form"><span className="eyebrow">MULAI DARI SINI</span><h2>{mode === 'login' ? 'Selamat datang kembali.' : 'Ruang pengetahuan Anda.'}</h2><p>{mode === 'login' ? 'Masuk untuk melanjutkan percakapan dengan Zenvix.' : 'Buat akun dan mulai percakapan pertama Anda.'}</p>
      {notice && <div className="info-notice">{notice}</div>}<ErrorNotice message={error}/>
      <form onSubmit={submit}><label htmlFor="email">Alamat email</label><input id="email" type="email" autoComplete="email" placeholder="nama@email.com" value={email} onChange={e => setEmail(e.target.value)} required maxLength={254} disabled={busy}/>
        <label htmlFor="password">Password</label><div className="password-wrap"><input id="password" type={visible ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder={mode === 'register' ? 'Minimal 8 karakter' : 'Masukkan password Anda'} value={password} onChange={e => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : 1} maxLength={72} disabled={busy}/><button type="button" className="icon-button" aria-label={visible ? 'Sembunyikan password' : 'Tampilkan password'} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={18}/> : <Eye size={18}/>}</button></div>
        {mode === 'register' && <small className="field-hint">Gunakan minimal 8 karakter. Maksimal 72 byte.</small>}
        <button className="button primary auth-submit" disabled={busy}>{busy ? <Spinner label="Sebentar…"/> : <>{mode === 'login' ? 'Masuk ke ruang kerja' : 'Buat akun'}<ArrowRight size={18}/></>}</button>
      </form><p className="auth-switch">{mode === 'login' ? 'Belum punya akun?' : 'Sudah punya akun?'} <button disabled={busy} onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>{mode === 'login' ? 'Daftar sekarang' : 'Masuk'}</button></p>
      <div className="privacy-note"><ShieldCheck size={19}/><span>Riwayat percakapan hanya dapat diakses oleh akun Anda.</span></div></div><footer>Belajar, menulis, dan berpikir bersama.</footer></section>
  </main>;
}
