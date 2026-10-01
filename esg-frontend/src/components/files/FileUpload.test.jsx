import { describe, it, expect, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FileUpload from './FileUpload';
import { checkFile } from './fileRules';
import { authenticated, renderWithStore } from '../../test/renderWithStore';
import { fakeApi, ok } from '../../test/fakeApi';

const pdf = (name = 'evidence.pdf', extra = '') => new File([`%PDF-1.4 ${extra}`], name, { type: 'application/pdf' });
const info = { storage: 'Local filesystem', maxBytes: 1024, allowedExtensions: 'csv, doc, docx, jpeg, jpg, pdf, png, txt, xls, xlsx', virusScanning: 'Not configured: files are stored unscanned' };
const stored = { id: 'f1', originalName: 'evidence.pdf', contentType: 'application/pdf', sizeBytes: 13, currentVersion: 1, createdAt: '2026-09-30T10:00:00Z', versions: [{ version: 1, scanStatus: 'not_scanned' }] };

describe('checkFile (mirrors FileTypePolicy)', () => {
  it.each([
    ['tool.exe', new File(['MZ'], 'tool.exe'), /Allowed file types/],
    ['fake.pdf', new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'fake.pdf'), /doesn't match its \.pdf/],
    ['binary.csv', new File([new Uint8Array([0x61, 0, 0x62])], 'binary.csv'), /doesn't match its \.csv/],
    ['empty.pdf', new File([], 'empty.pdf'), /empty/],
    ['big.pdf', pdf('big.pdf', 'x'.repeat(2000)), /larger than the 1\.0 KB limit/],
  ])('rejects %s', async (_, file, message) => {
    expect(await checkFile(file, 1024)).toMatch(message);
  });

  it.each([
    ['evidence.PDF', pdf('evidence.PDF')],
    ['data.csv', new File(['a,b\n1,2\n'], 'data.csv')],
    ['chart.png', new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47, 13, 10])], 'chart.png')],
  ])('accepts %s', async (_, file) => {
    expect(await checkFile(file, 1024)).toBeNull();
  });
});

describe('FileUpload', () => {
  const routes = (files, extra = {}) => ({
    'GET /files/storage-info': () => ok(info),
    'GET /files': () => ok(files()),
    ...extra,
  });

  it('uploads a valid file against the record and lists it with version and scan status', async () => {
    let files = [];
    const calls = fakeApi(routes(() => files, {
      'POST /files': () => { files = [stored]; return [201, { success: true, data: stored }]; },
    }));
    renderWithStore(<FileUpload resourceType="data_point" resourceId="dp-1" />,
      { preloadedState: authenticated(['files:read', 'files:create']) });
    expect(await screen.findByText('No files attached.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Upload' })).toBeEnabled());
    await userEvent.upload(screen.getByTestId('file-input'), pdf());
    expect(await screen.findByText(/Not scanned/)).toBeInTheDocument();
    const upload = calls.find((c) => c.key === 'POST /files');
    expect(upload.headers.get('Content-Type')).toMatch(/^multipart\/form-data/);
    expect(calls.find((c) => c.key === 'GET /files').query).toEqual({ resourceType: 'data_point', resourceId: 'dp-1' });
  });

  it("blocks a file the server would reject without calling the server", async () => {
    const calls = fakeApi(routes(() => []));
    renderWithStore(<FileUpload resourceType="data_point" resourceId="dp-1" />,
      { preloadedState: authenticated(['files:read', 'files:create']) });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Upload' })).toBeEnabled());
    fireEvent.change(screen.getByTestId('file-input'), { target: { files: [new File(['MZ'], 'tool.exe')] } });
    expect(await screen.findByText(/Allowed file types/)).toBeInTheDocument();
    expect(calls.some((c) => c.key === 'POST /files')).toBe(false);
  });

  it('shows the server problem detail when the upload is refused', async () => {
    fakeApi(routes(() => [], {
      'POST /files': () => [415, { type: 'about:blank', title: 'Unsupported Media Type', status: 415, detail: "The file's content doesn't match its .pdf extension", errorCode: 'ESG-415' }],
    }));
    renderWithStore(<FileUpload resourceType="data_point" resourceId="dp-1" />,
      { preloadedState: authenticated(['files:read', 'files:create']) });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Upload' })).toBeEnabled());
    await userEvent.upload(screen.getByTestId('file-input'), pdf());
    expect(await screen.findByText("The file's content doesn't match its .pdf extension")).toBeInTheDocument();
  });

  it('read-only users can download but not upload or delete', async () => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const calls = fakeApi(routes(() => [stored], {
      'GET /files/f1/download-url': () => ok({ url: '/api/v1/files/f1/content?version=1', direct: false }),
      'GET /files/f1/content': () => ok('pdf-bytes'),
    }));
    renderWithStore(<FileUpload resourceType="data_point" resourceId="dp-1" />, { preloadedState: authenticated(['files:read']) });
    await screen.findByText('evidence.pdf');
    expect(screen.queryByRole('button', { name: 'Upload' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Delete evidence.pdf')).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Download evidence.pdf'));
    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(calls.map((c) => c.key)).toContain('GET /files/f1/content');
    expect(calls.find((c) => c.key === 'GET /files/f1/content').headers.get('Authorization')).toBe('Bearer access-token');
  });
});
