// Bearer token disimpan oleh session provider, bukan oleh komponen halaman.
let accessToken = null;
export function setAccessToken(value) { accessToken = value; }
export class ApiError extends Error {
  constructor(message, status = 0, code = '', fields = {}) {
    super(message); this.status = status; this.code = code; this.fields = fields;
  }
}
// XHR exposes real upload progress; the response still waits for server/AI processing.
function upload(path, { method, body, headers, signal, onUploadProgress }) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const abort = () => { xhr.abort(); reject(signal.reason || new DOMException('Aborted', 'AbortError')); };
    const cleanup = () => signal.removeEventListener('abort', abort);
    xhr.open(method, path);
    xhr.timeout = 190000;
    Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
    xhr.upload.onprogress = event => onUploadProgress({
      percent: event.lengthComputable && event.total > 0 ? Math.min(100, Math.round(event.loaded / event.total * 100)) : null,
      complete: false,
    });
    xhr.upload.onload = () => onUploadProgress({ percent: 100, complete: true });
    xhr.onload = () => {
      cleanup();
      resolve(new Response(xhr.status === 204 ? null : xhr.responseText, {
        status: xhr.status, statusText: xhr.statusText,
        headers: { 'Content-Type': xhr.getResponseHeader('Content-Type') || 'application/json' },
      }));
    };
    xhr.onerror = xhr.ontimeout = xhr.onabort = () => { cleanup(); reject(new TypeError('Upload interrupted')); };
    if (signal.aborted) { abort(); return; }
    signal.addEventListener('abort', abort, { once: true });
    try { xhr.send(body); } catch (error) { cleanup(); reject(error); }
  });
}
export async function request(path, { method = 'GET', body, signal, blob = false, publicRequest = false, onUploadProgress } = {}) {
  const headers = {};
  const usedToken = publicRequest ? null : accessToken;
  if (usedToken) headers.Authorization = 'Bearer ' + usedToken;
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  let response;
  try {
    const requestSignal = signal ? AbortSignal.any([signal, AbortSignal.timeout(190000)]) : AbortSignal.timeout(190000);
    response = body instanceof FormData && onUploadProgress
      ? await upload('/api' + path, { method, headers, body, signal: requestSignal, onUploadProgress })
      : await fetch('/api' + path, {
      method, headers, credentials: 'omit', cache: 'no-store',
      signal: requestSignal,
      body: body ? (body instanceof FormData ? body : JSON.stringify(body)) : undefined,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError('Koneksi terputus atau terlalu lama. Coba lagi; pertanyaan yang sama akan memakai ID request sebelumnya.');
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    if (response.status === 401 && usedToken && usedToken === accessToken) {
      window.dispatchEvent(new Event('session-expired'));
    }
    throw new ApiError(data.message || 'Permintaan belum berhasil. Silakan coba lagi.', response.status, data.code, data.fields);
  }
  if (response.status === 204) return null;
  return blob ? response.blob() : response.json();
}
export async function allPages(path, signal) {
  const items = [];
  for (let page = 0; ; page++) {
    const data = await request(path + '?page=' + page + '&size=100', { signal });
    items.push(...data.items);
    if (!data.items.length || items.length >= data.totalElements) return items;
  }
}
export async function downloadDocument(id, filename) {
  const blob = await request('/documents/' + id + '/download', { blob: true });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
