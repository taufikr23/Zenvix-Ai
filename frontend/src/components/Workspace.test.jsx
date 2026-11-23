import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Workspace } from './Workspace';
import { allPages, request } from '../lib/api';
import { useSession } from '../lib/session';
vi.mock('../lib/api', () => ({ allPages: vi.fn(), request: vi.fn(), downloadDocument: vi.fn() }));
vi.mock('../lib/session', () => ({ useSession: vi.fn() }));
beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  vi.stubGlobal('scrollTo', vi.fn());
  Element.prototype.scrollIntoView = vi.fn();
  useSession.mockReturnValue({ session: { user: { email: 'test@example.test', role: 'USER' } }, logout: vi.fn() });
  allPages.mockReset().mockResolvedValue([]); request.mockReset();
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('uses full-height chat without a topbar and can close and reopen the desktop sidebar', async () => {
  render(<Workspace/>);
  await waitFor(() => expect(allPages).toHaveBeenCalled());
  expect(document.querySelector('.topbar')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Tutup sidebar' }));
  expect(document.getElementById('workspace-sidebar').getAttribute('aria-hidden')).toBe('true');
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Buka sidebar' }));
  fireEvent.click(screen.getByRole('button', { name: 'Buka sidebar' }));
  expect(document.getElementById('workspace-sidebar').getAttribute('aria-hidden')).toBe('false');
  expect(screen.getByRole('button', { name: 'Chat baru' })).toBeTruthy();
  expect(document.querySelector('#workspace-sidebar .sidebar-help').textContent).toBe('Panduan');
});
it('opens mobile navigation and closes it with Escape or the backdrop', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  render(<Workspace/>);
  await waitFor(() => expect(allPages).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: 'Buka sidebar' }));
  expect(document.querySelector('.workspace-main').hasAttribute('inert')).toBe(true);
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Tutup sidebar' }));
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(document.querySelector('.workspace-main').hasAttribute('inert')).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: 'Buka sidebar' }));
  fireEvent.click(screen.getByRole('button', { name: 'Tutup navigasi' }));
  expect(document.getElementById('workspace-sidebar').getAttribute('aria-hidden')).toBe('true');
});

it('opens chat first, creates without documentId and updates the conversation title', async () => {
  const createdAt = '2026-11-21T00:00:00Z';
  request.mockResolvedValueOnce({ id: 'g1', documentId: null, filename: null, title: 'Percakapan baru', createdAt })
    .mockResolvedValueOnce({ id: 't1', index: 0, question: 'Halo AI', answer: 'Halo!', sources: [], createdAt });
  render(<Workspace/>);
  expect(screen.getByRole('heading', { name: 'Zenvix.' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Chat baru' }));
  const input = await screen.findByRole('textbox', { name: 'Pesan untuk Zenvix' });
  await waitFor(() => expect(input.disabled).toBe(false));
  expect(request.mock.calls[0]).toEqual(['/conversations', { method: 'POST', body: {} }]);
  fireEvent.change(input, { target: { value: 'Halo AI' } });
  fireEvent.click(screen.getByRole('button', { name: 'Kirim pertanyaan' }));
  await screen.findByText('Halo!');
  expect(screen.getByRole('button', { name: 'Hapus percakapan Halo AI' })).toBeTruthy();
});

it('removes the document workspace and never requests its list', async () => {
  allPages.mockImplementation(path => path === '/documents' ? Promise.reject(new Error('Dokumen tidak tersedia')) : Promise.resolve([
    { id: 'g1', title: 'Belajar Python', documentId: null, createdAt: '2026-11-21T00:00:00Z' }
  ]));
  render(<Workspace/>);
  await screen.findByText('Belajar Python');
  expect(screen.getByRole('button', { name: 'Chat baru' }).disabled).toBe(false);
  expect(screen.queryByRole('alert')).toBeNull();
  expect(screen.queryByText('Dokumen saya')).toBeNull();
  expect(allPages.mock.calls.some(([path]) => path === '/documents')).toBe(false);
});
