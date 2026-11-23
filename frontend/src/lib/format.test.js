import { describe, expect, it } from 'vitest';
import { validateFile } from './format';
describe('file validation', () => {
  it('accepts all supported extensions including uppercase', () => {
    for (const name of ['catatan.TXT','dokumen.pdf','surat.docx']) expect(validateFile(new File(['teks'], name))).toBe('');
  });
  it('rejects unsupported or empty uploads', () => {
    expect(validateFile(new File(['x'], 'program.exe'))).toContain('PDF');
    expect(validateFile(new File([], 'kosong.txt'))).toContain('kosong');
    expect(validateFile(null)).toContain('Pilih');
  });
  it('uses the configured size limit', () => {
    expect(validateFile({ name: 'a.pdf', size: 11 * 1024 * 1024 }, 10)).toContain('10 MB');
    expect(validateFile({ name: 'a.pdf', size: 11 * 1024 * 1024 }, 12)).toBe('');
  });
});
