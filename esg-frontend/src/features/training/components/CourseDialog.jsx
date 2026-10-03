import { useState } from 'react';
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton, MenuItem, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Add, AttachFile, Delete } from '@mui/icons-material';
import { FormSelect, FormTextField } from '../../../components/form/FormFields';
import { checkFile } from '../../../components/files/fileRules';
import { useUploadFileMutation } from '../../../components/files/filesApi';
import { applyProblem } from '../../../services/formErrors';
import { useCoursesQuery, useCreateCourseMutation, useUpdateCourseMutation } from '../trainingApi';
import {
  CATEGORIES, DELIVERY, MODULE_KINDS, courseSchema, emptyCourse, emptyModule, emptyQuestion, optionList, toCourseBody, toCourseForm,
} from '../schemas';

const options = (o) => Object.entries(o).map(([value, label]) => ({ value, label }));

const cellSelect = (field, fs, label, entries) => (
  <TextField select size="small" fullWidth {...field} value={field.value ?? ''} error={!!fs.error} helperText={fs.error?.message}
    SelectProps={{ SelectDisplayProps: { 'aria-label': label } }}>
    {entries.map(([k, l]) => <MenuItem key={k} value={k}>{l}</MenuItem>)}
  </TextField>
);
const cellText = (field, fs, label, props = {}) => (
  <TextField size="small" fullWidth {...field} value={field.value ?? ''} error={!!fs.error} helperText={fs.error?.message}
    slotProps={{ htmlInput: { 'aria-label': label } }} {...props} />
);

/** A module's PDF: uploaded straight away (type and size checked as FileTypePolicy does); the row keeps the file id. */
function PdfCell({ field, fieldState, name, label, setName }) {
  const [upload, { isLoading }] = useUploadFileMutation();
  const [problem, setProblem] = useState('');
  const pick = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const issue = await checkFile(file);
    if (issue || file.type !== 'application/pdf') { setProblem(issue || 'choose a PDF'); return; }
    setProblem('');
    try {
      const saved = await upload({ file, resourceType: 'training_course' }).unwrap();
      field.onChange(saved.id);
      setName(file.name);
    } catch (err) {
      setProblem(err?.data?.detail ?? 'Upload failed');
    }
  };
  return (
    <Box>
      <Button size="small" component="label" startIcon={<AttachFile />} disabled={isLoading} aria-label={label}>
        {name || 'Attach PDF'}
        <input hidden type="file" accept="application/pdf" onChange={pick} />
      </Button>
      {(problem || fieldState.error) && <Typography variant="caption" color="error" display="block">{problem || fieldState.error.message}</Typography>}
    </Box>
  );
}

function ModuleRow({ control, index, onRemove, setValue }) {
  const kind = useWatch({ control, name: `modules.${index}.kind` });
  const fileName = useWatch({ control, name: `modules.${index}.fileName` });
  const n = index + 1;
  return (
    <TableRow>
      <TableCell sx={{ minWidth: 160 }}><Controller name={`modules.${index}.title`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Title, line ${n}`)} /></TableCell>
      <TableCell sx={{ minWidth: 120 }}><Controller name={`modules.${index}.kind`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Kind, line ${n}`, Object.entries(MODULE_KINDS))} /></TableCell>
      <TableCell sx={{ minWidth: 260 }}>
        {kind === 'text' && <Controller name={`modules.${index}.body`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Text, line ${n}`, { multiline: true, minRows: 2 })} />}
        {kind === 'video' && <Controller name={`modules.${index}.url`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Video link, line ${n}`, { placeholder: 'https://' })} />}
        {kind === 'pdf' && (
          <Controller name={`modules.${index}.fileId`} control={control} render={({ field, fieldState }) => (
            <PdfCell field={field} fieldState={fieldState} name={fileName} label={`PDF, line ${n}`} setName={(v) => setValue(`modules.${index}.fileName`, v)} />
          )} />
        )}
      </TableCell>
      <TableCell sx={{ width: 110 }}><Controller name={`modules.${index}.minutes`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Minutes, line ${n}`, { inputMode: 'numeric' })} /></TableCell>
      <TableCell><IconButton size="small" aria-label={`Remove module ${n}`} onClick={onRemove}><Delete fontSize="small" /></IconButton></TableCell>
    </TableRow>
  );
}

function QuestionRow({ control, index, onRemove }) {
  const text = useWatch({ control, name: `questions.${index}.options` });
  const count = Math.max(2, Math.min(6, optionList(text).length));
  const n = index + 1;
  return (
    <TableRow>
      <TableCell sx={{ minWidth: 220 }}><Controller name={`questions.${index}.question`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Question, line ${n}`, { multiline: true })} /></TableCell>
      <TableCell sx={{ minWidth: 220 }}><Controller name={`questions.${index}.options`} control={control} render={({ field, fieldState }) => cellText(field, fieldState, `Options, line ${n}`, { multiline: true, minRows: 2, placeholder: 'One option per line' })} /></TableCell>
      <TableCell sx={{ minWidth: 130 }}>
        <Controller name={`questions.${index}.correctIndex`} control={control} render={({ field, fieldState }) => cellSelect(field, fieldState, `Correct answer, line ${n}`,
          Array.from({ length: Math.max(count, Number(field.value) + 1 || 0) }, (_, i) => [String(i), `Option ${i + 1}`]))} />
      </TableCell>
      <TableCell><IconButton size="small" aria-label={`Remove question ${n}`} onClick={onRemove}><Delete fontSize="small" /></IconButton></TableCell>
    </TableRow>
  );
}

/** A course (D37 (1)–(2)): details, modules (text, PDF or video link) and the multiple-choice quiz. Editable only as a draft. */
export function CourseDialog({ course, onClose }) {
  const editing = Boolean(course?.id);
  const [create, c] = useCreateCourseMutation();
  const [update, u] = useUpdateCourseMutation();
  const { data: all = [] } = useCoursesQuery();
  const [formError, setFormError] = useState('');
  const { control, handleSubmit, setError, setValue, formState: { errors } } = useForm({
    defaultValues: editing ? toCourseForm(course) : emptyCourse, resolver: zodResolver(courseSchema),
  });
  const modules = useFieldArray({ control, name: 'modules' });
  const questions = useFieldArray({ control, name: 'questions' });
  const delivery = useWatch({ control, name: 'delivery' });
  const others = all.filter((x) => x.id !== course?.id);
  const titles = Object.fromEntries(all.map((x) => [x.id, x.title]));
  const submit = handleSubmit(async (v) => {
    setFormError('');
    try {
      if (editing) await update({ id: course.id, ...toCourseBody(v), version: course.version }).unwrap();
      else await create(toCourseBody(v)).unwrap();
      onClose();
    } catch (err) {
      setFormError(applyProblem(err, setError, { knownFields: Object.keys(emptyCourse) }));
    }
  });
  return (
    <Dialog open onClose={() => onClose()} maxWidth="lg" fullWidth>
      <form onSubmit={submit} noValidate>
        <DialogTitle>{editing ? `Edit ${course.title}` : 'New course'}</DialogTitle>
        <DialogContent>
          {formError && <Alert severity="error" sx={{ mb: 2 }}>{formError}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, sm: 3 }}><FormTextField control={control} name="code" label="Course code" helperText="e.g. ESG-101" /></Grid>
            <Grid size={{ xs: 12, sm: 9 }}><FormTextField control={control} name="title" label="Title" /></Grid>
            <Grid size={12}><FormTextField control={control} name="description" label="Description (optional)" multiline minRows={2} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="category" label="Category" options={options(CATEGORIES)} /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}><FormSelect control={control} name="delivery" label="Delivery" options={options(DELIVERY)} /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="durationHours" label="Duration (hours)" inputMode="decimal" helperText="Credited as training hours on completion" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="passingScore" label="Pass mark (%)" inputMode="numeric" /></Grid>
            <Grid size={{ xs: 6, sm: 4 }}><FormTextField control={control} name="certificateValidityMonths" label="Certificate valid for (months, optional)" inputMode="numeric" helperText="Blank: doesn't expire" /></Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Controller name="prerequisiteIds" control={control} render={({ field, fieldState }) => (
                <TextField select fullWidth size="small" label="Prerequisites (optional)" value={field.value} inputRef={field.ref} onBlur={field.onBlur}
                  onChange={(e) => field.onChange(e.target.value)} error={!!fieldState.error} helperText={fieldState.error?.message ?? 'Courses to pass first'}
                  SelectProps={{ multiple: true, displayEmpty: true, renderValue: (sel) => (sel.length ? sel.map((id) => titles[id] ?? 'Course').join(', ') : 'None') }}
                  slotProps={{ inputLabel: { shrink: true } }}>
                  {others.map((x) => <MenuItem key={x.id} value={x.id}>{x.code} · {x.title}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
          </Grid>

          <Box sx={{ display: 'flex', alignItems: 'center', mt: 3, mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>Modules</Typography>
            <Button size="small" startIcon={<Add />} onClick={() => modules.append({ ...emptyModule })}>Add module</Button>
          </Box>
          {errors.modules?.message && <Alert severity="error" sx={{ mb: 1 }}>{errors.modules.message}</Alert>}
          {modules.fields.length === 0 ? <Typography variant="body2" color="text.secondary">A course needs at least one module before it can be published.</Typography> : (
            <TableContainer>
              <Table size="small" aria-label="Modules">
                <TableHead><TableRow><TableCell>Title</TableCell><TableCell>Kind</TableCell><TableCell>Content</TableCell><TableCell>Minutes</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {modules.fields.map((f, i) => <ModuleRow key={f.id} control={control} index={i} setValue={setValue} onRemove={() => modules.remove(i)} />)}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          <Box sx={{ display: 'flex', alignItems: 'center', mt: 3, mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>Quiz {delivery === 'classroom' && '(not used for classroom courses)'}</Typography>
            <Button size="small" startIcon={<Add />} onClick={() => questions.append({ ...emptyQuestion })}>Add question</Button>
          </Box>
          {errors.questions?.message && <Alert severity="error" sx={{ mb: 1 }}>{errors.questions.message}</Alert>}
          {questions.fields.length === 0 ? <Typography variant="body2" color="text.secondary">No quiz: an online course is completed when every module is done.</Typography> : (
            <TableContainer>
              <Table size="small" aria-label="Quiz questions">
                <TableHead><TableRow><TableCell>Question</TableCell><TableCell>Options (one per line)</TableCell><TableCell>Correct answer</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {questions.fields.map((f, i) => <QuestionRow key={f.id} control={control} index={i} onRemove={() => questions.remove(i)} />)}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={c.isLoading || u.isLoading}>{editing ? 'Save changes' : 'Create course'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
