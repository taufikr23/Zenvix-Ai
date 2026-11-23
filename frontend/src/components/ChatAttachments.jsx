import { useEffect, useState } from 'react';
import { Download, FileText, X } from 'lucide-react';
import { request } from '../lib/api';

export const ACCEPTED_ATTACHMENTS = '.pdf,.docx,.txt,.png,.jpg,.jpeg';
export function validateAttachments(files) {
  if (files.length > 3) return 'Maksimal 3 lampiran per pesan.';
  if (files.some(file => !/\.(pdf|docx|txt|png|jpe?g)$/i.test(file.name))) return 'Pilih PDF, DOCX, TXT, PNG, atau JPG.';
  if (files.some(file => file.size === 0 || file.size > 5 * 1024 * 1024)) return 'Setiap file harus berisi data dan maksimal 5 MB.';
  if (files.reduce((total, file) => total + file.size, 0) > 10 * 1024 * 1024) return 'Total lampiran maksimal 10 MB.';
  return '';
}
const sizeLabel = size => size < 1024 * 1024 ? `${Math.max(1, Math.ceil(size / 1024))} KB` : `${(size / (1024 * 1024)).toFixed(1)} MB`;

function LocalPreview({ file }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    if (!/\.(png|jpe?g)$/i.test(file.name)) return;
    const preview = URL.createObjectURL(file); setUrl(preview);
    return () => URL.revokeObjectURL(preview);
  }, [file]);
  return url ? <img className="attachment-thumbnail" src={url} alt={file.name}/> : <FileText size={24}/>;
}
export function PendingAttachments({ files, onRemove, disabled, label = 'Lampiran yang akan dikirim' }) {
  return files.length > 0 && <div className="attachment-list" aria-label={label}>{files.map((file, index) =>
    <div className="attachment-card" key={index}><LocalPreview file={file}/><span><strong title={file.name}>{file.name}</strong><small>{sizeLabel(file.size)}</small></span>
      {onRemove && <button type="button" className="icon-button" aria-label={'Hapus lampiran ' + file.name} disabled={disabled} onClick={() => onRemove(index)}><X size={16}/></button>}
    </div>)}</div>;
}

function SavedAttachment({ file, conversationId, onError }) {
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const path = '/conversations/' + conversationId + '/attachments/' + file.id;
  useEffect(() => {
    if (!file.mediaType.startsWith('image/')) return;
    const controller = new AbortController(); let preview;
    request(path, { blob: true, signal: controller.signal }).then(blob => {
      if (!controller.signal.aborted) { preview = URL.createObjectURL(blob); setUrl(preview); }
    }).catch(() => { /* Keep the download button available if preview loading fails. */ });
    return () => { controller.abort(); if (preview) URL.revokeObjectURL(preview); };
  }, [path, file.mediaType]);
  async function download() {
    setBusy(true);
    try {
      const blob = await request(path, { blob: true });
      const href = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = href; link.download = file.filename; document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(href), 1000);
    } catch (failure) { onError(failure.message); } finally { setBusy(false); }
  }
  return <button type="button" className={'attachment-card saved-attachment ' + (url ? 'image-attachment' : '')} onClick={download} disabled={busy} aria-label={'Unduh lampiran ' + file.filename}>
    {url ? <img src={url} alt={file.filename} loading="lazy"/> : <FileText size={24}/>}
    <span><strong title={file.filename}>{file.filename}</strong><small>{busy ? 'Mengunduh…' : sizeLabel(file.size)}</small></span><Download size={16}/>
  </button>;
}
export function SavedAttachments({ files = [], conversationId, onError }) {
  return files.length > 0 && <div className="attachment-list saved-attachments">{files.map(file => <SavedAttachment key={file.id} file={file} conversationId={conversationId} onError={onError}/>)}</div>;
}
