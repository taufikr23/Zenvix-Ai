import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatPanel } from './ChatPanel';
import { allPages, request } from '../lib/api';
vi.mock('../lib/api', () => ({ allPages: vi.fn(), request: vi.fn() }));
const conversation = { id: 'c1', documentId: 'd1', filename: 'catatan.txt', createdAt: '2026-11-17T00:00:00Z' };
const source = { documentId: 'd1', filename: 'catatan.txt', chunkId: 'chunk1', chunkIndex: 0, pageNumber: null, quote: 'Biaya seminar Rp150.000.' };
function mount(extra = {}) {
  const onSource = vi.fn();
  render(<ChatPanel selected={conversation} documents={[]} conversations={[conversation]} currentDoc={{ id: 'd1', fileType: 'TXT' }} loading={false} busy={false} onSelect={vi.fn()} onBrowse={vi.fn()} onNew={vi.fn()} onDelete={vi.fn()} onSource={onSource} {...extra}/>);
  return onSource;
}
beforeEach(() => { allPages.mockReset().mockResolvedValue([]); request.mockReset(); Element.prototype.scrollIntoView = vi.fn(); URL.createObjectURL = vi.fn(() => 'blob:preview'); URL.revokeObjectURL = vi.fn(); });
afterEach(cleanup);
it('immediately moves submitted text and files into the chat while upload and AI are pending', async () => {
  let finish;
  request.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  mount({ selected: { id: 'general', documentId: null } });
  const input = screen.getByRole('textbox');
  await waitFor(() => expect(input.disabled).toBe(false));
  const file = new File(['pdf'], 'Materi 4.pdf', { type: 'application/pdf' });
  fireEvent.change(screen.getByLabelText('Pilih file atau gambar'), { target: { files: [file] } });
  fireEvent.change(input, { target: { value: 'Apa isi dokumen tersebut?' } });
  fireEvent.keyDown(input, { key: 'Enter' });
  expect(input.value).toBe('');
  expect(screen.queryByLabelText('Lampiran yang akan dikirim')).toBeNull();
  expect(within(screen.getByLabelText('Riwayat pesan')).getByText('Materi 4.pdf')).toBeTruthy();
  expect(screen.getByText('Apa isi dokumen tersebut?')).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Hapus lampiran Materi 4.pdf' })).toBeNull();
  expect(screen.getByRole('status').textContent).toContain('Mengunggah lampiran');
  fireEvent.keyDown(input, { key: 'Enter' });
  expect(request).toHaveBeenCalledTimes(1);
  act(() => request.mock.calls[0][1].onUploadProgress({ percent: 50, complete: false }));
  expect(screen.getByRole('status').textContent).toContain('50%');
  act(() => request.mock.calls[0][1].onUploadProgress({ percent: 100, complete: true }));
  expect(screen.getByRole('status').textContent).toContain('Zenvix sedang membaca lampiran');
  await act(async () => finish({ id: 't1', question: 'Apa isi dokumen tersebut?', answer: 'Isi dokumen.', sources: [], attachments: [{ id: 'a1', filename: file.name, mediaType: file.type, size: file.size }], createdAt: conversation.createdAt }));
  expect(screen.getAllByText('Apa isi dokumen tersebut?')).toHaveLength(1);
  expect(screen.getByRole('button', { name: 'Unduh lampiran Materi 4.pdf' })).toBeTruthy();
  expect(screen.queryByRole('status')).toBeNull();
  expect(input.disabled).toBe(false);
});
it('retries an uncertain response using the same request ID and displays verified sources', async () => {
  request.mockRejectedValueOnce(new Error('Koneksi terputus')).mockResolvedValueOnce({ id: 't1', requestId: 'unused', question: 'Berapa biaya?', answer: 'Biaya seminar Rp150.000. [1]', found: true, sources: [source], createdAt: conversation.createdAt });
  const onSource = mount(); const user = userEvent.setup();
  await waitFor(() => expect(screen.getByRole('textbox', { name: 'Pertanyaan tentang dokumen' }).disabled).toBe(false));
  await user.type(screen.getByRole('textbox'), 'Berapa biaya?');
  await user.click(screen.getByRole('button', { name: 'Kirim pertanyaan' }));
  await screen.findByRole('alert');
  await user.click(screen.getByRole('button', { name: 'Coba kirim lagi' }));
  await screen.findByText('Biaya seminar Rp150.000. [1]');
  expect(request.mock.calls[0][1].body.requestId).toBe(request.mock.calls[1][1].body.requestId);
  await user.click(screen.getByRole('button', { name: /1.*catatan.txt/ }));
  expect(onSource).toHaveBeenCalledWith(source);
});
it('gives an edited question a new request ID after failure', async () => {
  request.mockRejectedValue(new Error('Gagal')); mount(); const user = userEvent.setup();
  await waitFor(() => expect(screen.getByRole('textbox').disabled).toBe(false));
  await user.type(screen.getByRole('textbox'), 'Pertama');
  await user.click(screen.getByRole('button', { name: 'Kirim pertanyaan' })); await screen.findByRole('alert');
  await user.clear(screen.getByRole('textbox')); await user.type(screen.getByRole('textbox'), 'Kedua');
  await user.click(screen.getByRole('button', { name: 'Coba kirim lagi' }));
  await waitFor(() => expect(request).toHaveBeenCalledTimes(2));
  expect(request.mock.calls[0][1].body.requestId).not.toBe(request.mock.calls[1][1].body.requestId);
});
it('renders document text as text, not executable HTML', async () => {
  const attack = '<img src=x onerror=alert(1)>';
  allPages.mockResolvedValue([{ id: 't1', question: attack, answer: attack, sources: [], createdAt: conversation.createdAt }]);
  mount(); await screen.findAllByText(attack);
  expect(document.querySelector('.answer-text img, .user-message img')).toBeNull();
});
it('blocks sending when loading history fails and allows a reload', async () => {
  allPages.mockRejectedValueOnce(new Error('Riwayat gagal')).mockResolvedValueOnce([]);
  mount(); await screen.findByRole('alert');
  expect(screen.getByRole('textbox').disabled).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: 'Muat ulang riwayat' }));
  await waitFor(() => expect(screen.getByRole('textbox').disabled).toBe(false));
  expect(request).not.toHaveBeenCalled();
});

it('sends general messages without requiring a document and shows code safely', async () => {
  const general = { id: 'general', documentId: null, filename: null, title: 'Percakapan baru', createdAt: conversation.createdAt };
  const onTurn = vi.fn();
  const code = '<script>alert(1)</script>';
  request.mockResolvedValue({ id: 't2', index: 0, question: 'Buat kode', answer: 'Contoh:\n```html\n' + code + '\n```', sources: [], createdAt: conversation.createdAt });
  mount({ selected: general, conversations: [general], currentDoc: undefined, onTurn });
  const input = screen.getByRole('textbox', { name: 'Pesan untuk Zenvix' });
  await waitFor(() => expect(input.disabled).toBe(false));
  fireEvent.change(input, { target: { value: 'Buat kode' } });
  fireEvent.click(screen.getByRole('button', { name: 'Kirim pertanyaan' }));
  await screen.findByText(code);
  expect(document.querySelector('pre code').textContent).toBe(code);
  expect(document.querySelector('script')).toBeNull();
  expect(request.mock.calls[0][0]).toBe('/conversations/general/messages');
  expect(onTurn).toHaveBeenCalledWith('general', expect.objectContaining({ index: 0 }));
  expect(input.maxLength).toBe(100000);
});

it('offers a general conversation when history is empty', () => {
  const onNewGeneral = vi.fn();
  mount({ selected: null, conversations: [], onNewGeneral });
  fireEvent.click(screen.getByRole('button', { name: 'Mulai percakapan' }));
  expect(onNewGeneral).toHaveBeenCalledOnce();
});

it('sends a long pasted message intact including its ending', async () => {
  mount({ selected: { id: 'general', documentId: null } });
  const input=screen.getByRole('textbox');
  await waitFor(() => expect(input.disabled).toBe(false));
  const longText='Awal\n'+'a'.repeat(95000)+'\nPENUTUP PENTING';
  request.mockResolvedValue({ id: 'long', question: longText, answer: 'Terbaca lengkap.', sources: [], createdAt: conversation.createdAt });
  fireEvent.change(input,{target:{value:longText}});
  fireEvent.click(screen.getByRole('button',{name:'Kirim pertanyaan'}));
  await screen.findByText('Terbaca lengkap.');
  expect(request.mock.calls[0][1].body.question).toBe(longText);
  expect(input.maxLength).toBe(100000);
});

it('sends attachments without text and retains them with the same request ID on retry', async () => {
  request.mockRejectedValueOnce(new Error('Koneksi terputus')).mockResolvedValueOnce({ id: 't3', question: 'Tolong analisis lampiran ini.', answer: 'Lampiran terbaca.', sources: [], attachments: [], createdAt: conversation.createdAt });
  mount({ selected: { id: 'general', documentId: null } });
  await waitFor(() => expect(screen.getByRole('textbox').disabled).toBe(false));
  const file = new File(['Catatan rapat'], 'catatan.txt', { type: 'text/plain' });
  fireEvent.change(screen.getByLabelText('Pilih file atau gambar'), { target: { files: [file] } });
  expect(screen.getByText('catatan.txt')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Kirim pertanyaan' }));
  await screen.findByRole('alert');
  expect(screen.getByText('catatan.txt')).toBeTruthy();
  const first = request.mock.calls[0][1].body;
  expect(first).toBeInstanceOf(FormData);
  expect(first.getAll('files')[0].name).toBe('catatan.txt');
  const readBlob = blob => new Promise(resolve => { const reader = new FileReader(); reader.onload = () => resolve(JSON.parse(reader.result)); reader.readAsText(blob); });
  const firstMessage = await readBlob(first.get('message'));
  expect(firstMessage.question).toBe('Tolong analisis lampiran ini.');
  fireEvent.click(screen.getByRole('button', { name: 'Coba kirim lagi' }));
  await screen.findByText('Lampiran terbaca.');
  expect((await readBlob(request.mock.calls[1][1].body.get('message'))).requestId).toBe(firstMessage.requestId);
  expect(screen.queryByLabelText('Lampiran yang akan dikirim')).toBeNull();
});

it('previews and removes pasted images and rejects oversized or unsupported files', async () => {
  mount({ selected: { id: 'general', documentId: null } });
  await waitFor(() => expect(screen.getByRole('textbox').disabled).toBe(false));
  const image = new File(['image'], 'gambar.png', { type: 'image/png' });
  fireEvent.paste(screen.getByRole('textbox'), { clipboardData: { files: [image] } });
  expect(await screen.findByAltText('gambar.png')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Hapus lampiran gambar.png' }));
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  expect(screen.getByRole('button', { name: 'Kirim pertanyaan' }).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Pilih file atau gambar'), { target: { files: [new File(['x'], 'app.exe')] } });
  expect((await screen.findByRole('alert')).textContent).toContain('Pilih PDF');
  const large = new File(['x'], 'besar.txt'); Object.defineProperty(large, 'size', { value: 6 * 1024 * 1024 });
  fireEvent.change(screen.getByLabelText('Pilih file atau gambar'), { target: { files: [large] } });
  expect(screen.getByRole('alert').textContent).toContain('maksimal 5 MB');
  expect(request).not.toHaveBeenCalled();
});
