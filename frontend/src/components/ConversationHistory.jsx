import { MessageSquare, Trash2 } from 'lucide-react';
import { date } from '../lib/format';
import { Spinner } from './UI';

export function ConversationHistory({ conversations, selected, loading, busy, onSelect, onDelete }) {
  return <section className="sidebar-history" aria-label="Riwayat percakapan">
    <div className="conversation-rail-title"><h2>Riwayat</h2><span>{conversations.length}</span></div>
    <div className="history-list">
      {loading ? <Spinner/> : conversations.length ? conversations.map(item => {
        const title = item.title || item.filename || 'Percakapan baru';
        return <div className={'conversation-item ' + (selected?.id === item.id ? 'selected' : '')} key={item.id}>
          <button className="conversation-select" title={title} aria-current={selected?.id === item.id ? 'page' : undefined} onClick={() => onSelect(item)}>
            <MessageSquare size={17}/><span><strong>{title}</strong><small>{item.documentId ? 'Dokumen · ' : ''}{date(item.createdAt)}</small></span>
          </button>
          <button className="icon-button" aria-label={'Hapus percakapan ' + title} disabled={busy} onClick={() => onDelete(item)}><Trash2 size={15}/></button>
        </div>;
      }) : <p className="no-conversations">Percakapan Anda akan tersimpan di sini.</p>}
    </div>
  </section>;
}
