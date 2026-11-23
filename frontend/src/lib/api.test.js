import { afterEach, describe, expect, it, vi } from 'vitest';
import { request, setAccessToken, allPages } from './api';
afterEach(() => { vi.unstubAllGlobals(); setAccessToken(null); });
function response(status, body) { return { ok: status < 400, status, json: async () => body }; }
describe('API boundaries', () => {
  it('sends Bearer only on authenticated calls and never in the URL', async () => {
    const fetch = vi.fn().mockResolvedValue(response(200, { ok: true })); vi.stubGlobal('fetch', fetch);
    setAccessToken('token-test');
    await request('/documents');
    expect(fetch.mock.calls[0][0]).toBe('/api/documents');
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer token-test');
    expect(fetch.mock.calls[0][1].credentials).toBe('omit');
    await request('/auth/login', { publicRequest: true, method: 'POST', body: { email: 'u@example.test', password: 'test-only' } });
    expect(fetch.mock.calls[1][1].headers.Authorization).toBeUndefined();
  });
  it('expires the current session on protected 401 but not invalid login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401, { message: 'Tidak sah' })));
    const expired = vi.fn(); window.addEventListener('session-expired', expired);
    setAccessToken('token-test');
    await expect(request('/documents')).rejects.toMatchObject({ status: 401 });
    await expect(request('/auth/login', { publicRequest: true })).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledTimes(1); window.removeEventListener('session-expired', expired);
  });
  it('an old request cannot expire a newly authenticated session', async () => {
    let finish; vi.stubGlobal('fetch', vi.fn(() => new Promise(resolve => { finish = resolve; })));
    const expired = vi.fn(); window.addEventListener('session-expired', expired);
    setAccessToken('old'); const oldRequest = request('/documents'); setAccessToken('new');
    finish(response(401, {})); await expect(oldRequest).rejects.toMatchObject({ status: 401 });
    expect(expired).not.toHaveBeenCalled(); window.removeEventListener('session-expired', expired);
  });
  it('collects all pages rather than silently dropping older history', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(200, { items: [{ id: 1 }], totalElements: 2 }))
      .mockResolvedValueOnce(response(200, { items: [{ id: 2 }], totalElements: 2 }));
    vi.stubGlobal('fetch', fetch);
    expect(await allPages('/conversations')).toEqual([{ id: 1 }, { id: 2 }]);
    expect(fetch.mock.calls[1][0]).toContain('page=1&size=100');
  });
  it('lets the browser set multipart boundaries and accepts no-content responses', async () => {
    const fetch = vi.fn().mockResolvedValue(response(204)); vi.stubGlobal('fetch', fetch);
    const body = new FormData(); body.append('file', new File(['abc'], 'a.txt'));
    expect(await request('/documents', { method: 'POST', body })).toBeNull();
    expect(fetch.mock.calls[0][1].headers['Content-Type']).toBeUndefined();
    expect(fetch.mock.calls[0][1].body).toBe(body);
  });
  function fakeUpload() {
    const xhr = { upload: {}, open: vi.fn(), setRequestHeader: vi.fn(), send: vi.fn(), abort: vi.fn(), getResponseHeader: () => 'application/json', status: 200, statusText: 'OK', responseText: '{"id":"sent"}' };
    vi.stubGlobal('XMLHttpRequest', class { constructor() { return xhr; } });
    return xhr;
  }
  it('reports real upload progress before the server response while retaining auth and multipart boundaries', async () => {
    const xhr = fakeUpload(), progress = vi.fn();
    setAccessToken('upload-token');
    const body = new FormData(); body.append('files', new File(['abc'], 'a.txt'));
    const pending = request('/conversations/c1/messages', { method: 'POST', body, onUploadProgress: progress });
    expect(xhr.open).toHaveBeenCalledWith('POST', '/api/conversations/c1/messages');
    expect(xhr.setRequestHeader.mock.calls).toEqual([['Authorization', 'Bearer upload-token']]);
    expect(xhr.send).toHaveBeenCalledWith(body);
    xhr.upload.onprogress({ loaded: 50, total: 100, lengthComputable: true });
    expect(progress).toHaveBeenLastCalledWith({ percent: 50, complete: false });
    xhr.upload.onload();
    expect(progress).toHaveBeenLastCalledWith({ percent: 100, complete: true });
    xhr.onload();
    expect(await pending).toEqual({ id: 'sent' });
  });
  it('retains structured server errors and authentication handling for uploads', async () => {
    const xhr = fakeUpload(), expired = vi.fn();
    window.addEventListener('session-expired', expired); setAccessToken('current');
    const pending = request('/messages', { method: 'POST', body: new FormData(), onUploadProgress: vi.fn() });
    xhr.status = 401; xhr.responseText = '{"message":"Tidak sah","code":"UNAUTHORIZED"}'; xhr.onload();
    await expect(pending).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(expired).toHaveBeenCalledOnce(); window.removeEventListener('session-expired', expired);
  });
  it('rejects interrupted uploads without pretending they completed', async () => {
    const xhr = fakeUpload(), progress = vi.fn();
    const pending = request('/messages', { method: 'POST', body: new FormData(), onUploadProgress: progress });
    xhr.ontimeout();
    await expect(pending).rejects.toThrow('Koneksi terputus');
    expect(progress).not.toHaveBeenCalled();
  });
  it('aborts uploads when their caller cancels the request', async () => {
    const xhr = fakeUpload(), controller = new AbortController();
    const pending = request('/messages', { method: 'POST', body: new FormData(), signal: controller.signal, onUploadProgress: vi.fn() });
    controller.abort();
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(xhr.abort).toHaveBeenCalledOnce();
  });
});
