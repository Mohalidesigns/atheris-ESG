import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { FormCurrencyField, FormDateField, FormNumberField, FormSelect, FormTextField } from './FormFields';
import { applyProblem } from '../../services/formErrors';

const problem = (errors, detail = 'Validation failed') => ({ status: 400, data: { title: 'Validation failed', status: 400, detail, errors } });

describe('applyProblem (RFC 7807 → form)', () => {
  it('puts field errors on fields, renaming server fields and array indexes', () => {
    const setError = vi.fn();
    const message = applyProblem(problem({ numericValue: 'must be zero or more', 'rows[2].unit': 'too long' }), setError,
      { fieldMap: { numericValue: 'value' } });
    expect(message).toBe('');
    expect(setError).toHaveBeenCalledWith('value', { type: 'server', message: 'must be zero or more' });
    expect(setError).toHaveBeenCalledWith('rows.2.unit', { type: 'server', message: 'too long' });
  });

  it('returns a message for errors that have no field on the form', () => {
    const setError = vi.fn();
    const message = applyProblem(problem({ sort: 'can sort by name' }), setError, { knownFields: ['name'] });
    expect(message).toBe('Validation failed: sort: can sort by name');
    expect(setError).not.toHaveBeenCalled();
  });

  it('returns the problem detail when there are no field errors, and a network message offline', () => {
    expect(applyProblem({ status: 409, data: { detail: 'Someone else changed this factor.' } }, vi.fn())).toBe('Someone else changed this factor.');
    expect(applyProblem({ status: 'FETCH_ERROR' }, vi.fn())).toBe('Cannot reach the server');
  });
});

const schema = z.object({
  name: z.string().min(1, 'Name is required'),
  amount: z.string().regex(/^\d+(\.\d{1,2})?$/, 'Enter an amount'),
  cost: z.string(),
  day: z.string().min(1, 'Date is required'),
  pillar: z.enum(['E', 'S'], { message: 'Choose a pillar' }),
});

function Kit({ onValid, serverError }) {
  const { control, handleSubmit, setError } = useForm({
    defaultValues: { name: '', amount: '', cost: '', day: '', pillar: '' }, resolver: zodResolver(schema),
  });
  const submit = handleSubmit((v) => (serverError ? applyProblem(serverError, setError) : onValid(v)));
  return (
    <form onSubmit={submit} noValidate>
      <FormTextField control={control} name="name" label="Name" />
      <FormNumberField control={control} name="amount" label="Amount" unit="kWh" />
      <FormCurrencyField control={control} name="cost" label="Cost" currency="NGN" />
      <FormDateField control={control} name="day" label="Day" />
      <FormSelect control={control} name="pillar" label="Pillar" options={[{ value: 'E', label: 'Environmental' }, { value: 'S', label: 'Social' }]} />
      <button type="submit">Save</button>
    </form>
  );
}

describe('form kit fields', () => {
  it('show client validation messages and submit string values', async () => {
    const onValid = vi.fn();
    render(<Kit onValid={onValid} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Name is required')).toBeInTheDocument();
    expect(screen.getByText('Enter an amount')).toBeInTheDocument();
    expect(screen.getByText('Date is required')).toBeInTheDocument();
    expect(screen.getByText('Choose a pillar')).toBeInTheDocument();
    expect(screen.getByText('kWh')).toBeInTheDocument();
    expect(screen.getByText('NGN')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'Lagos HQ');
    await userEvent.type(screen.getByLabelText('Amount'), '12.5');
    await userEvent.type(screen.getByLabelText('Cost'), '1000');
    await userEvent.type(screen.getByLabelText('Day'), '2026-03-31');
    await userEvent.click(screen.getByLabelText('Pillar'));
    await userEvent.click(await screen.findByRole('option', { name: 'Social' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onValid).toHaveBeenCalledWith({ name: 'Lagos HQ', amount: '12.5', cost: '1000', day: '2026-03-31', pillar: 'S' }));
  });

  it('show server field errors next to the input', async () => {
    render(<Kit serverError={problem({ amount: 'must be a whole number' })} />);
    await userEvent.type(screen.getByLabelText('Name'), 'x');
    await userEvent.type(screen.getByLabelText('Amount'), '1');
    await userEvent.type(screen.getByLabelText('Day'), '2026-03-31');
    await userEvent.click(screen.getByLabelText('Pillar'));
    await userEvent.click(await screen.findByRole('option', { name: 'Environmental' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('must be a whole number')).toBeInTheDocument();
  });
});
