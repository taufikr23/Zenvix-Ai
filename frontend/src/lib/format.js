export const statusLabels = { UPLOADED: 'Menunggu', QUEUED: 'Dalam antrean', PROCESSING: 'Membaca dokumen', EXTRACTED: 'Menyiapkan AI', READY: 'Siap ditanya', FAILED: 'Gagal diproses', INDEX_FAILED: 'AI belum siap', DELETING: 'Menghapus' };
export const processing = status => ['UPLOADED', 'QUEUED', 'PROCESSING', 'EXTRACTED'].includes(status);
export function bytes(value) { return value < 1024 * 1024 ? Math.max(1, Math.round(value / 1024)) + ' KB' : (value / 1024 / 1024).toFixed(1) + ' MB'; }
export function date(value) { return new Intl.DateTimeFormat('id-ID', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value)); }
export function time(value) { return new Intl.DateTimeFormat('id-ID', { hour: '2-digit', minute: '2-digit' }).format(new Date(value)); }
export function validateFile(file, maxMb = 10) {
  if (!file) return 'Pilih satu dokumen terlebih dahulu.';
  if (!/\.(pdf|docx|txt)$/i.test(file.name)) return 'Gunakan file PDF, DOCX, atau TXT.';
  if (!file.size) return 'File kosong tidak dapat diunggah.';
  if (file.size > maxMb * 1024 * 1024) return 'Ukuran file melebihi batas ' + maxMb + ' MB.';
  return '';
}
