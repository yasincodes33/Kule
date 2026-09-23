import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { Btn, Field } from '../../shared/components/primitives';
import type { ProjectResponse } from '../../shared/api/types';
import { useCreateProjectMutation, useUpdateProjectMutation } from './hooks';

/** Oluşturma VE düzenleme için tek form — BoardPage'in "ilk proje" ekranıyla ProjectsPage
 * arasında kopyalanmasın diye (bkz. AuthLayout'un çözdüğü aynı tekrar sorunu). */
export function ProjectForm({ project, onDone }: { project?: ProjectResponse; onDone: () => void }) {
  const [name, setName] = useState(project?.name ?? '');
  const [repoUrl, setRepoUrl] = useState(project?.repoUrl ?? '');
  const [defaultBranch, setDefaultBranch] = useState(project?.defaultBranch ?? 'main');
  const create = useCreateProjectMutation();
  const update = useUpdateProjectMutation();
  const pending = create.isPending || update.isPending;
  const mutationError = create.error ?? update.error;
  const error = mutationError instanceof ApiError ? mutationError.message : mutationError ? 'Proje kaydedilemedi' : null;
  const valid = name.trim().length > 0 && repoUrl.trim().length > 0;

  const submit = async () => {
    if (!valid) return;
    if (project) {
      await update.mutateAsync({ projectId: project.id, name: name.trim(), repoUrl: repoUrl.trim(), defaultBranch: defaultBranch.trim() || 'main' });
    } else {
      await create.mutateAsync({ name: name.trim(), repoUrl: repoUrl.trim(), defaultBranch: defaultBranch.trim() || 'main' });
    }
    onDone();
  };

  return (
    <>
      {error && <p className="k-sub k-err" style={{ marginBottom: 'var(--space-3)' }}>{error}</p>}
      <Field label="PROJE ADI">
        <input className="k-input mono" value={name} onChange={(e) => setName(e.target.value)} placeholder="billing-svc" onKeyDown={(e) => e.key === 'Enter' && submit()} autoFocus />
      </Field>
      <Field label="REPO URL">
        <input className="k-input mono" value={repoUrl} onChange={(e) => setRepoUrl(e.target.value)} placeholder="https://github.com/org/billing-svc" onKeyDown={(e) => e.key === 'Enter' && submit()} />
      </Field>
      <Field label="VARSAYILAN BRANCH">
        <input className="k-input mono" value={defaultBranch} onChange={(e) => setDefaultBranch(e.target.value)} placeholder="main" onKeyDown={(e) => e.key === 'Enter' && submit()} />
      </Field>
      <Btn kind="pri" icon={project ? 'check' : 'plus'} disabled={!valid || pending} onClick={submit}>
        {pending ? 'KAYDEDİLİYOR…' : project ? 'Kaydet' : 'Proje oluştur'}
      </Btn>
    </>
  );
}
