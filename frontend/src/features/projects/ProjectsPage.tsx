import { useState } from 'react';
import { Btn, Panel, Reveal } from '../../shared/components/primitives';
import { useProjectsListQuery } from './hooks';
import { ProjectForm } from './ProjectForm';
import type { ProjectResponse } from '../../shared/api/types';

export default function ProjectsPage() {
  const projectsQuery = useProjectsListQuery();
  const projects = projectsQuery.data ?? [];
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<ProjectResponse | null>(null);

  return (
    <div className="k-page">
      <div className="k-page-h">
        <div>
          <span className="k-kicker mono">DEPO / YAPILANDIRMA</span>
          <h3>Projeler</h3>
        </div>
        <div className="k-page-actions">
          <Btn kind="pri" icon="plus" onClick={() => { setCreating(true); setEditing(null); }}>Yeni proje</Btn>
        </div>
      </div>

      {creating && (
        <Panel kicker="YENİ PROJE" title="Proje oluştur" right={<button className="k-link mono" onClick={() => setCreating(false)}>KAPAT</button>}>
          <ProjectForm onDone={() => setCreating(false)} />
        </Panel>
      )}

      {editing && (
        <Panel kicker="DÜZENLE" title={editing.name} right={<button className="k-link mono" onClick={() => setEditing(null)}>KAPAT</button>}>
          <ProjectForm project={editing} onDone={() => setEditing(null)} />
        </Panel>
      )}

      {projectsQuery.isLoading ? (
        <p className="k-sub">Yükleniyor…</p>
      ) : projects.length === 0 && !creating ? (
        <Panel kicker="BOŞ" title="Bu organizasyonda henüz proje yok">
          <p className="k-sub">Görevlerin bağlanacağı ilk projeyi kurmak için yukarıdaki "Yeni proje" düğmesini kullan.</p>
        </Panel>
      ) : (
        <div className="k-tablewrap">
          <table className="k-table">
            <thead><tr>{['PROJE', 'REPO', 'BRANCH', ''].map((h) => <th key={h} className="mono">{h}</th>)}</tr></thead>
            <tbody>
              {projects.map((p, i) => (
                <Reveal key={p.id} i={i} tag="tr">
                  <td>{p.name}</td>
                  <td className="mono k-sub" style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.repoUrl}</td>
                  <td className="mono k-sub">{p.defaultBranch}</td>
                  <td className="k-td-r">
                    <button className="k-link mono" onClick={() => { setEditing(p); setCreating(false); }}>DÜZENLE</button>
                  </td>
                </Reveal>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
