import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { SessionProvider, useSession } from './session';
import { request, setAccessToken } from './api';
vi.mock('./api', () => ({ request: vi.fn(), setAccessToken: vi.fn() }));
const session = { token: 'test-token', user: { id: 'u1', email: 'u@example.test' }, expiresAt: Date.now() + 60000 };
function Probe() {
  const value = useSession();
  return <><span>{value.checking ? 'checking' : value.session ? value.session.user.email : 'signed-out'}</span><span>{value.notice}</span><button onClick={() => value.logout()}>Logout</button></>;
}
beforeEach(() => { sessionStorage.clear(); request.mockReset().mockResolvedValue(session.user); setAccessToken.mockClear(); });
afterEach(() => { cleanup(); vi.useRealTimers(); });
it('restores a session only after verifying the user with the backend', async () => {
  sessionStorage.setItem('documind.session', JSON.stringify(session));
  render(<SessionProvider><Probe/></SessionProvider>);
  await screen.findByText(session.user.email);
  expect(request).toHaveBeenCalledWith('/auth/me', expect.any(Object));
  fireEvent.click(screen.getByText('Logout'));
  await screen.findByText('signed-out');
  expect(sessionStorage.getItem('documind.session')).toBeNull();
  expect(setAccessToken).toHaveBeenLastCalledWith(null);
});
it('drops already expired stored sessions before loading private data', async () => {
  sessionStorage.setItem('documind.session', JSON.stringify({ ...session, expiresAt: Date.now() - 1 }));
  render(<SessionProvider><Probe/></SessionProvider>);
  await screen.findByText('signed-out'); expect(request).not.toHaveBeenCalled();
});
it('clears private session on an authentication failure event', async () => {
  sessionStorage.setItem('documind.session', JSON.stringify(session));
  render(<SessionProvider><Probe/></SessionProvider>); await screen.findByText(session.user.email);
  act(() => window.dispatchEvent(new Event('session-expired')));
  await screen.findByText('signed-out');
  expect(sessionStorage.getItem('documind.session')).toBeNull();
  expect(screen.getByText(/Sesi Anda berakhir/)).toBeTruthy();
});
