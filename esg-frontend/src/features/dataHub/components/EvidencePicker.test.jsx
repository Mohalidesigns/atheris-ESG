import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import EvidencePicker from './EvidencePicker';
import { authenticated, renderWithStore } from '../../../test/renderWithStore';
import { fakeApi, ok } from '../../../test/fakeApi';

function Harness() {
  const [value, setValue] = useState([]);
  return (
    <>
      <EvidencePicker value={value} onChange={setValue} resourceType="data_point" max={3} />
      <output aria-label="ids">{value.join(',')}</output>
    </>
  );
}

describe('EvidencePicker (Phase 2)', () => {
  it('attaches several files in one pick, shows the accepted types and limit, and reports each refused file', async () => {
    let n = 0;
    fakeApi({
      'GET /files/storage-info': () => ok({ storage: 'Local', maxBytes: 52428800, allowedExtensions: 'csv, pdf, png', virusScanning: 'Sandbox', virusScanningSandbox: true }),
      'POST /files': () => { n += 1; return [201, { success: true, data: { id: `f${n}`, originalName: `file${n}.pdf`, scanStatus: 'clean' }, message: 'File uploaded' }]; },
    });
    renderWithStore(<Harness />, { preloadedState: authenticated(['files:create', 'files:read']) });
    expect(await screen.findByLabelText('Accepted files')).toHaveTextContent('csv, pdf, png · up to 50.0 MB each · 3 files at most');
    const input = screen.getByTestId('evidence-input');
    expect(input).toHaveAttribute('multiple');
    const pdf = (name) => new File(['%PDF-1.4 x'], name, { type: 'application/pdf' });
    // applyAccept off: a browser's file dialog can still return other types ("All files"); the picker checks them.
    await userEvent.setup({ applyAccept: false }).upload(input, [pdf('a.pdf'), pdf('b.pdf'), new File(['MZ'], 'tool.exe')]);
    await waitFor(() => expect(screen.getByLabelText('ids')).toHaveTextContent('f1,f2'));
    expect(screen.getByText(/tool\.exe: Allowed file types/)).toBeInTheDocument();
  });
});
