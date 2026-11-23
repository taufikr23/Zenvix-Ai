import { useEffect, useRef } from 'react';
import { FileText, LoaderCircle, X, AlertCircle, Check, Sparkles } from 'lucide-react';
import { statusLabels, processing } from '../lib/format';
import { PlanetScene } from './PlanetScene';
export function Logo({ className = '' }) { return <img className={'zenvix-logo ' + className} src="/zenvix-logo-transparent.png" alt=""/>; }
export function Brand() { return <div className="brand"><Logo/><span>Zenvix<span className="brand-ai">AI</span></span></div>; }
export function Spinner({ label = 'Memuat…' }) { return <span className="loading"><LoaderCircle size={18} className="spin"/>{label}</span>; }
export function ErrorNotice({ message }) { return message ? <div className="error-notice" role="alert"><AlertCircle size={18}/><span>{message}</span></div> : null; }
export function Status({ value }) { return <span className={'status status-' + value}>{value === 'READY' ? <Check size={12}/> : processing(value) ? <LoaderCircle size={12} className="spin"/> : <span className="status-dot"/>}{statusLabels[value] || value}</span>; }
export function FileIcon({ type = 'TXT', large = false }) { return <span className={'file-icon file-' + type + (large ? ' large' : '')}><FileText size={large ? 30 : 22}/><small>{type}</small></span>; }
export function Empty({ title, description, action, chat = false }) { return <div className="empty">{chat ? <div className="welcome-logo"><PlanetScene/></div> : <span className="empty-icon"><FileText size={30}/></span>}<h2>{title}</h2><p>{description}</p>{action}</div>; }
export function Modal({ title, children, onClose, busy = false, wide = false }) {
  const ref = useRef(null);
  useEffect(() => { const dialog = ref.current; dialog.showModal(); return () => dialog.close(); }, []);
  return <dialog ref={ref} aria-label={title} className={'modal ' + (wide ? 'modal-wide' : '')} onCancel={event => { event.preventDefault(); if (!busy) onClose(); }} onClick={event => { if (event.target === ref.current && !busy) onClose(); }}>
    <div className="modal-inner"><header><h2>{title}</h2><button className="icon-button" aria-label="Tutup dialog" disabled={busy} onClick={onClose}><X size={20}/></button></header>{children}</div>
  </dialog>;
}
