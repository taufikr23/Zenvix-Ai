import { useRef, useState } from 'react';
import { UploadCloud, X, ArrowUpRight } from 'lucide-react';
import { Modal, ErrorNotice, Spinner, FileIcon } from './UI';
import { request } from '../lib/api';
import { bytes, validateFile } from '../lib/format';
const maxMb = Number(import.meta.env.VITE_MAX_UPLOAD_MB || 10);
export function UploadModal({ onClose, onUploaded }) {
  const input = useRef(null); const [file, setFile] = useState(null);
  const [drag, setDrag] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  function choose(files) {
    setError('');
    if (files.length !== 1) { setError('Unggah satu dokumen dalam satu waktu.'); return; }
    const message = validateFile(files[0], maxMb);
    if (message) { setError(message); return; }
    setFile(files[0]);
  }
  async function upload(event) {
    event.preventDefault(); const message = validateFile(file, maxMb);
    if (message) { setError(message); return; }
    setBusy(true); setError('');
    const data = new FormData(); data.append('file', file);
    try { onUploaded(await request('/documents', { method: 'POST', body: data })); }
    catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  return <Modal title="Tambahkan pengetahuan baru" onClose={onClose} busy={busy}>
    <p className="modal-description">Unggah dokumen untuk mulai mencari jawaban dari isinya.</p>
    <form onSubmit={upload}><input ref={input} type="file" className="sr-only" aria-label="Pilih dokumen" accept=".pdf,.docx,.txt" disabled={busy} onChange={event => { choose([...event.target.files]); event.target.value = ''; }}/>
      <button type="button" className={'dropzone ' + (drag ? 'dragging' : '')} disabled={busy} onClick={() => input.current.click()} onDragOver={event => { event.preventDefault(); setDrag(true); }} onDragLeave={() => setDrag(false)} onDrop={event => { event.preventDefault(); setDrag(false); if (!busy) choose([...event.dataTransfer.files]); }}>
        <span className="upload-circle"><UploadCloud size={27}/></span><strong>Pilih file atau seret ke sini</strong><span>PDF, DOCX, TXT · Maksimal {maxMb} MB</span>
      </button>
      {file && <div className="selected-file"><FileIcon type={file.name.split('.').pop().toUpperCase()}/><div><strong className="break-word">{file.name}</strong><small>{bytes(file.size)}</small></div><button className="icon-button" type="button" aria-label="Batalkan pilihan file" disabled={busy} onClick={() => setFile(null)}><X size={17}/></button></div>}
      <ErrorNotice message={error}/><p className="field-hint">Dokumen Anda bersifat pribadi. Teks yang relevan dikirim ke Gemini untuk membantu menjawab pertanyaan.</p>
      <div className="modal-actions"><button type="button" className="button secondary" disabled={busy} onClick={onClose}>Batal</button><button type="submit" className="button primary" disabled={!file || busy}>{busy ? <Spinner label="Mengunggah…"/> : <>Unggah dokumen <ArrowUpRight size={17}/></>}</button></div>
    </form>
  </Modal>;
}
