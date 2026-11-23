import { useCallback, useEffect, useRef, useState } from 'react';
import { MessageSquare, LogOut, Plus, PanelLeftOpen, PanelLeftClose, ArrowUpRight, ShieldCheck, CircleHelp } from 'lucide-react';
import { useSession } from '../lib/session';
import { allPages, request, downloadDocument } from '../lib/api';
import { Brand, ErrorNotice, Modal, Spinner, FileIcon } from './UI';
import { ChatPanel } from './ChatPanel';
import { ConversationHistory } from './ConversationHistory';
export function Workspace() {
  const { session, logout } = useSession();
  const revision = useRef(0);
  const [compact, setCompact] = useState(() => window.matchMedia('(max-width: 760px)').matches);
  useEffect(() => { const media = window.matchMedia('(max-width: 760px)'); const update = () => setCompact(media.matches); media.addEventListener('change', update); return () => media.removeEventListener('change', update); }, []);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [view, setView] = useState('chat'); const [mobileNav, setMobileNav] = useState(false);
  const openSidebarButton = useRef(null); const closeSidebarButton = useRef(null);
  const sidebarOpen = compact ? mobileNav : !sidebarCollapsed;
  function closeSidebar() {
    if (compact) setMobileNav(false); else setSidebarCollapsed(true);
  }
  const previousSidebarOpen = useRef(sidebarOpen);
  useEffect(() => {
    if (previousSidebarOpen.current !== sidebarOpen) {
      (sidebarOpen ? closeSidebarButton : openSidebarButton).current?.focus();
      previousSidebarOpen.current = sidebarOpen;
    }
  }, [sidebarOpen]);
  useEffect(() => {
    if (!compact || !mobileNav) return;
    const onKeyDown = event => {
      if (document.querySelector('dialog[open]')) return;
      if (event.key === 'Escape') { event.preventDefault(); setMobileNav(false); }
      if (event.key === 'Tab') {
        const items = Array.from(document.querySelectorAll('#workspace-sidebar button:not(:disabled), #workspace-sidebar a[href]'));
        const first = items[0], last = items.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [compact, mobileNav]);
  const [conversations, setConversations] = useState([]);
  const [selected, setSelected] = useState(null); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(''); const [toast, setToast] = useState('');
  const [confirm, setConfirm] = useState(null);
  const [source, setSource] = useState(null); const [help, setHelp] = useState(false);
  const [busy, setBusy] = useState(false); const [modalError, setModalError] = useState('');
  useEffect(() => { window.scrollTo(0, 0); }, []);
  const refresh = useCallback(async signal => {
    const startedAtRevision = revision.current;
    const chats = await allPages('/conversations', signal);
    // An older refresh must not remove a chat created while it was in flight.
    if (signal?.aborted || startedAtRevision !== revision.current) return;
    setConversations(chats);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    refresh(controller.signal).catch(failure => { if (!controller.signal.aborted) setError(failure.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [refresh]);
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(''), 4500); return () => clearTimeout(timer); }, [toast]);
  function navigate(next) { setView(next); setMobileNav(false); setError(''); window.scrollTo(0, 0); }
  async function newGeneralChat() {
    if (busy) return;
    setError(''); setBusy(true);
    try {
      const conversation = await request('/conversations', { method: 'POST', body: {} });
      revision.current++;
      setConversations(current => [conversation, ...current]);
      setSelected(conversation); navigate('chat');
    } catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  function updateTitle(id, turn) {
    if (turn.index !== 0) return;
    revision.current++;
    const title = Array.from(turn.question.replace(/\s+/g, ' ').trim()).slice(0, 80).join('');
    setConversations(current => current.map(item => item.id === id ? { ...item, title } : item));
    setSelected(current => current?.id === id ? { ...current, title } : current);
  }
  async function download(doc, reportError = setError) {
    setError(''); try { await downloadDocument(doc.id, doc.filename); } catch (failure) { reportError(failure.message); }
  }
  async function remove() {
    setBusy(true); setModalError('');
    try {
      await request('/conversations/' + confirm.item.id, { method: 'DELETE' });
      revision.current++;
      setConversations(current => current.filter(item => item.id !== confirm.item.id));
      if (selected?.id === confirm.item.id) setSelected(null);
      setConfirm(null); setToast('Berhasil dihapus.');
    } catch (failure) { setModalError(failure.message); } finally { setBusy(false); }
  }
  function confirmDelete(kind, item) { setModalError(''); setConfirm({ kind, item }); }
  return <div className={'app-shell assistant-shell ' + (view === 'chat' ? 'chat-shell ' : '') + (!compact && sidebarCollapsed ? 'sidebar-collapsed' : '')}>
    {mobileNav && <button className="nav-backdrop" aria-label="Tutup navigasi" onClick={() => setMobileNav(false)}/>}
    <aside id="workspace-sidebar" inert={!sidebarOpen} aria-hidden={!sidebarOpen} className={'sidebar ' + (mobileNav ? 'open' : '')}><div className="sidebar-brand"><Brand/><button ref={closeSidebarButton} className="icon-button sidebar-close" aria-label="Tutup sidebar" title="Tutup sidebar" aria-controls="workspace-sidebar" aria-expanded={sidebarOpen} onClick={closeSidebar}><PanelLeftClose size={20}/></button></div>
      <button className="button primary sidebar-new-chat" disabled={busy} onClick={newGeneralChat}><Plus size={18}/>Chat baru</button>
      <nav aria-label="Navigasi utama">
        <button className={view === 'chat' ? 'nav-item active' : 'nav-item'} onClick={() => navigate('chat')}><MessageSquare size={19}/>Percakapan<span>{conversations.length}</span></button>
      </nav>
      <ConversationHistory conversations={conversations} selected={selected} loading={loading} busy={busy} onSelect={item => { setSelected(item); navigate('chat'); }} onDelete={item => confirmDelete('conversation', item)}/>
      <div className="sidebar-bottom"><button className="nav-item sidebar-help" onClick={() => setHelp(true)}><CircleHelp size={17}/>Panduan</button><div className="private-label"><ShieldCheck size={15}/>Ruang kerja pribadi</div><div className="user-block"><span className="avatar">{session.user.email[0].toUpperCase()}</span><div><strong title={session.user.email}>{session.user.email.split('@')[0]}</strong><small>{session.user.role === 'ADMIN' ? 'Admin' : 'Akun pribadi'}</small></div><button className="icon-button" aria-label="Keluar" title="Keluar" onClick={() => logout()}><LogOut size={17}/></button></div></div>
    </aside>
    <div className={'workspace-main ' + (!sidebarOpen ? 'sidebar-is-hidden' : '')} inert={compact && mobileNav}>
      {!sidebarOpen && <button ref={openSidebarButton} className="icon-button sidebar-reopen" aria-controls="workspace-sidebar" aria-expanded={false} aria-label="Buka sidebar" title="Buka sidebar" onClick={() => compact ? setMobileNav(true) : setSidebarCollapsed(false)}><PanelLeftOpen size={22}/></button>}
      <main className={'main-content ' + (view === 'chat' ? 'chat-main' : '')}>
        <ErrorNotice message={error}/>
        <ChatPanel selected={selected} busy={busy} onNewGeneral={newGeneralChat} onTurn={updateTitle} onSource={value => { setModalError(''); setSource(value); }}/>
      </main>{view !== 'chat' && <footer className="workspace-footer">Zenvix <span>Teman berpikir untuk kebutuhan sehari-hari.</span></footer>}
    </div>
    {toast && <div className="toast" role="status">{toast}</div>}
    {confirm && <Modal title="Hapus percakapan?" busy={busy} onClose={() => setConfirm(null)}><p className="modal-description"><strong className="break-word">{confirm.item.title || confirm.item.filename || 'Percakapan baru'}</strong></p><p className="modal-description">Seluruh pesan dan lampiran dalam percakapan ini akan dihapus.</p><ErrorNotice message={modalError}/><div className="modal-actions"><button className="button secondary" disabled={busy} onClick={() => setConfirm(null)}>Batal</button><button className="button danger" disabled={busy} onClick={remove}>{busy ? <Spinner label="Menghapus…"/> : 'Ya, hapus'}</button></div></Modal>}
    {source && <Modal title="Sumber jawaban" wide onClose={() => setSource(null)}><div className="source-document"><FileIcon type={source.filename.split('.').pop().toUpperCase()}/><div><strong className="break-word">{source.filename}</strong><p>{source.pageNumber ? 'Halaman ' + source.pageNumber : 'Nomor halaman tidak tersedia'} · Potongan {source.chunkIndex + 1}</p></div></div><span className="eyebrow">KUTIPAN DARI DOKUMEN</span><blockquote className="source-quote">{source.quote}</blockquote><ErrorNotice message={modalError}/><p className="field-hint">Kutipan ini diperiksa terhadap teks dokumen. Baca konteks lengkap untuk memastikan kesesuaiannya.</p><div className="modal-actions"><button className="button secondary" onClick={() => setSource(null)}>Tutup</button><button className="button primary" onClick={() => download({ id: source.documentId, filename: source.filename }, setModalError)}>Unduh dokumen asli <ArrowUpRight size={16}/></button></div></Modal>}
    {help && <Modal title="Kenali ruang kerja Anda" onClose={() => setHelp(false)}><ol className="help-steps"><li><strong>Mulai chat baru</strong><p>Klik Chat baru untuk belajar, menulis, membuat kode, menerjemahkan, atau berdiskusi tanpa mengunggah dokumen.</p></li><li><strong>Lanjutkan percakapan</strong><p>AI memakai pesan terbaru sebagai konteks. Riwayat tersimpan di akun Anda dan dapat dibuka kembali.</p></li><li><strong>Kirim file dan gambar</strong><p>Klik tombol lampiran di kolom pesan untuk mengirim PDF berisi teks, DOCX, TXT, PNG, atau JPG. Maksimal 3 file, 5 MB per file, dan total 10 MB per pesan.</p></li></ol><p className="info-notice">AI dapat membuat kesalahan dan tidak mengakses internet secara langsung. Periksa kembali informasi penting.</p><button className="button primary" onClick={() => setHelp(false)}>Mengerti <CheckIcon/></button></Modal>}
  </div>;
}
function CheckIcon() { return <span aria-hidden="true">✓</span>; }
