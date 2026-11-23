import { useEffect, useRef, useState } from 'react';
import { ArrowUp, Plus, BookOpen, Sparkles, ArrowUpRight, RefreshCw, Paperclip } from 'lucide-react';
import { allPages, request } from '../lib/api';
import { time } from '../lib/format';
import { Empty, ErrorNotice, FileIcon, Spinner, Logo } from './UI';
import { AnswerContent } from './AnswerContent';
import { ACCEPTED_ATTACHMENTS, validateAttachments, PendingAttachments, SavedAttachments } from './ChatAttachments';
import { PlanetScene } from './PlanetScene';
import { ImageGallery } from './ImageGallery';

const title = item => item.title || item.filename || 'Percakapan baru';
const MAX_MESSAGE_CHARACTERS = 100000;
const isImageQuery = text => Boolean(text && /\b(foto|gambar|potret|wallpaper|ilustrasi|lukisan)\b/i.test(text));
export function ChatPanel({ selected, currentDoc, busy, onNew, onNewGeneral, onSource, onTurn }) {
  return <section className="chat-workspace" aria-label="Percakapan AI">
    <h1 className="visually-hidden">Zenvix.</h1>
    {selected ? <Conversation key={selected.id} conversation={selected} document={currentDoc} onNew={onNew} busy={busy} onSource={onSource} onTurn={onTurn}/> :
      <div className="chat-placeholder"><Empty chat title="Ada yang bisa saya bantu?" description="Belajar, menulis, membuat kode, atau sekadar berdiskusi. Mulai dengan satu pertanyaan." action={<button className="button primary" disabled={busy} onClick={onNewGeneral}>Mulai percakapan <ArrowUpRight size={17}/></button>}/></div>}
  </section>;
}

function Conversation({ conversation, document, onNew, busy, onSource, onTurn }) {
  const isDocument = Boolean(conversation.documentId);
  const [turns, setTurns] = useState([]); const [loading, setLoading] = useState(true);
  const [question, setQuestion] = useState(''); const [sending, setSending] = useState(false);
  const [error, setError] = useState(''); const [pending, setPending] = useState(null);
  const inFlight = useRef(false);
  const [uploadProgress, setUploadProgress] = useState(null);
  const [files, setFiles] = useState([]); const fileInput = useRef(null);
  const [dragging, setDragging] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false); const [loadVersion, setLoadVersion] = useState(0);
  const bottom = useRef(null); const textarea = useRef(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(''); setLoadFailed(false);
    allPages('/conversations/' + conversation.id + '/messages', controller.signal).then(setTurns).catch(failure => {
      if (!controller.signal.aborted) { setError(failure.message); setLoadFailed(true); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [conversation.id, loadVersion]);
  useEffect(() => { if (turns.length || sending) bottom.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }); }, [turns, sending]);
  async function send(event) {
    event?.preventDefault(); const text = question.trim() || (files.length ? 'Tolong analisis lampiran ini.' : '');
    if (!text || inFlight.current || loading || loadFailed) return;
    if (text.length > MAX_MESSAGE_CHARACTERS) { setError('Pesan maksimal 100.000 karakter. Kirim teks yang lebih panjang sebagai file TXT.'); return; }
    const payload = pending?.question === text && pending.files === files ? pending : { requestId: crypto.randomUUID(), question: text, files };
    inFlight.current = true;
    setPending(payload); setSending(true); setError(''); setUploadProgress(null);
    setQuestion(''); setFiles([]);
    try {
      let body = { requestId: payload.requestId, question: text };
      if (payload.files.length) {
        const multipart = new FormData();
        multipart.append('message', new Blob([JSON.stringify(body)], { type: 'application/json' }));
        payload.files.forEach(file => multipart.append('files', file)); body = multipart;
      }
      const turn = await request('/conversations/' + conversation.id + '/messages', {
        method: 'POST', body,
        ...(payload.files.length ? { onUploadProgress: setUploadProgress } : {}),
      });
      setTurns(current => current.some(item => item.id === turn.id) ? current : [...current, turn]);
      onTurn?.(conversation.id, turn);
      setPending(null);
    } catch (failure) {
      setQuestion(payload.question); setFiles(payload.files); setError(failure.message);
    } finally { inFlight.current = false; setSending(false); }
  }
  useEffect(() => { if (!sending && !loading) textarea.current?.focus(); }, [sending, loading]);
  function addFiles(incoming) {
    if (isDocument || sending || loading || loadFailed) return;
    const next = [...files, ...Array.from(incoming)];
    const failure = validateAttachments(next);
    if (failure) { setError(failure); return; }
    setFiles(next); setError('');
  }
  const suggestions = isDocument ? ['Apa tujuan yang disebutkan dalam dokumen ini?', 'Tanggal penting apa yang tercantum?', 'Apa persyaratan yang disebutkan?'] :
    ['Jelaskan konsep pemrograman dengan contoh sederhana.', 'Bantu saya menyusun artikel yang menarik.', 'Terjemahkan dan perbaiki tulisan saya.', 'Bantu saya menganalisis suatu masalah.'];
  return <section className={'conversation ' + (isDocument ? 'document-conversation' : 'general-conversation')}>
    {isDocument && <header className="chat-document-header">
      {isDocument ? <FileIcon type={document?.fileType || conversation.filename?.split('.').pop().toUpperCase()}/> : <span className="assistant-avatar"><Sparkles size={22}/></span>}
      <div><strong title={title(conversation)}>{title(conversation)}</strong><span><span className="ready-dot"/>{isDocument ? conversation.filename : 'Asisten untuk berbagai kebutuhan Anda'}</span></div>
      {isDocument && <button className="icon-button bordered" aria-label="Percakapan baru dengan dokumen ini" title="Percakapan baru" disabled={busy || sending || !document} onClick={onNew}><Plus size={18}/></button>}
    </header>}
    <div className={'messages ' + (!turns.length && !sending ? 'messages-empty' : '')} aria-label="Riwayat pesan" aria-busy={loading || sending}>
      {loading ? <div className="list-loading"><Spinner label="Memuat percakapan."/></div> : !turns.length && !sending && !loadFailed ?
        <div className="chat-intro"><div className="welcome-logo"><PlanetScene/></div>
          <h2>{isDocument ? 'Mari pahami dokumen ini.' : 'Apa yang ingin Anda kerjakan?'}</h2>
          <p>{isDocument ? 'Tanyakan fakta dalam dokumen, lalu telusuri sumber jawabannya.' : 'Dari pertanyaan sederhana hingga ide besar, mulai di sini.'}</p>
          <div className="question-suggestions">{suggestions.map(text => <button key={text} onClick={() => { setQuestion(text); textarea.current?.focus(); }}>{text}<ArrowUpRight size={15}/></button>)}</div>
        </div> : null}
      {turns.map(turn => <div className="turn" key={turn.id}>
        <div className="user-message"><span className="message-label">ANDA</span><SavedAttachments files={turn.attachments} conversationId={conversation.id} onError={setError}/><p>{turn.question}</p></div>
        <div className="assistant-message"><span className="assistant-avatar"><Logo/></span><div className="assistant-content">
          <span className="message-label">ZENVIX <small>{time(turn.createdAt)}</small></span><AnswerContent text={turn.answer}/>
          {turn.images?.length > 0 && <ImageGallery images={turn.images}/>}
          {turn.sources.length > 0 && <div className="answer-sources"><span className="source-label"><BookOpen size={13}/>SUMBER JAWABAN</span>
            {turn.sources.map((source, index) => <button key={source.chunkId} className="source-chip" onClick={() => onSource(source)}><span className="source-index">{index + 1}</span><span>{source.filename}{source.pageNumber ? ' · Hal. ' + source.pageNumber : ''}</span><ArrowUpRight size={14}/></button>)}
          </div>}
        </div></div>
      </div>)}
      {sending && pending && <div className="turn"><div className="user-message"><span className="message-label">ANDA</span><PendingAttachments files={pending.files} label="Lampiran pesan yang sedang diproses"/><p>{pending.question}</p></div><div className="assistant-message"><span className="assistant-avatar"><Logo/></span><div role="status" aria-live="polite"><Spinner label={pending.files.length ? uploadProgress?.complete ? 'Unggahan selesai. Zenvix sedang membaca lampiran dan menyusun jawaban.' : `Mengunggah lampiran${uploadProgress?.percent != null ? ` (${uploadProgress.percent}%)` : ''}…` : isImageQuery(pending.question) ? 'Zenvix sedang mencari gambar dan menyusun jawaban…' : 'Sedang menyusun jawaban.'}/></div></div></div>}
      <div ref={bottom}/>
    </div>
    <div className="composer-area"><ErrorNotice message={error}/>
      {loadFailed && <button className="text-button" onClick={() => setLoadVersion(v => v + 1)}><RefreshCw size={14}/>Muat ulang riwayat</button>}
      <form className={'composer ' + (dragging ? 'composer-dragging' : '')} onSubmit={send}
        onDragOver={e => { e.preventDefault(); if (!isDocument && !sending && !loading && !loadFailed) setDragging(true); }}
        onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget)) setDragging(false); }}
        onDrop={e => { e.preventDefault(); setDragging(false); addFiles(e.dataTransfer.files); }}
        onPaste={e => { const pasted = Array.from(e.clipboardData?.files || []); if (pasted.length && !isDocument) { e.preventDefault(); addFiles(pasted); } }}>
        <PendingAttachments files={files} disabled={sending} onRemove={index => setFiles(current => current.filter((_, i) => i !== index))}/>
        {dragging && <p className="attachment-hint" role="status">Lepaskan file atau gambar di sini</p>}
        <textarea ref={textarea} aria-label={isDocument ? 'Pertanyaan tentang dokumen' : 'Pesan untuk Zenvix'} placeholder={isDocument ? 'Tanyakan sesuatu tentang dokumen ini.' : 'Tanyakan apa saja atau tempel kode Anda…'} value={question} maxLength={MAX_MESSAGE_CHARACTERS} rows={2} disabled={sending || loading || loadFailed} onChange={e => setQuestion(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send(); } }}/>
        <div className="composer-bottom">{!isDocument && <><input ref={fileInput} type="file" className="visually-hidden" tabIndex={-1} aria-label="Pilih file atau gambar" accept={ACCEPTED_ATTACHMENTS} multiple disabled={sending || loading || loadFailed} onChange={e => { addFiles(e.target.files); e.target.value = ''; }}/><button type="button" className="icon-button attach-button" aria-label="Lampirkan file atau gambar" title="Lampirkan file atau gambar" disabled={sending || loading || loadFailed || files.length >= 3} onClick={() => fileInput.current?.click()}><Paperclip size={20}/></button></>}<span>{question.length.toLocaleString('id-ID')}/100.000 <span className="composer-hint">· Shift + Enter untuk baris baru</span></span><button className="send-button" type="submit" aria-label={pending && error ? 'Coba kirim lagi' : 'Kirim pertanyaan'} disabled={(!question.trim() && !files.length) || sending || loading || loadFailed}>{sending ? <RefreshCw size={18} className="spin"/> : <ArrowUp size={20}/>}</button></div>
      </form>{!isDocument && <p className="attachment-hint">PDF, DOCX, TXT, PNG, JPG · Maks. 3 file, 5 MB/file, total 10 MB</p>}
      <p className="chat-disclaimer">AI dapat membuat kesalahan. Periksa kembali informasi penting.</p>
    </div>
  </section>;
}
