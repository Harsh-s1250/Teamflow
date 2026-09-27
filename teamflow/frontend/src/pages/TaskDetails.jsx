import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import StatusBadge from "../components/StatusBadge";
import PriorityBadge from "../components/PriorityBadge";
import Breadcrumbs from "../components/Breadcrumbs";
import ConfirmDialog from "../components/ConfirmDialog";
import { useAuth } from "../context/AuthContext";

const STATUS_OPTIONS = ["TODO", "IN_PROGRESS", "BLOCKED", "COMPLETED"];

export default function TaskDetails() {
  const { id } = useParams();
  const { user, isManager } = useAuth();

  const [task, setTask] = useState(null);
  const [project, setProject] = useState(null);
  const [members, setMembers] = useState([]);
  const [dependencies, setDependencies] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [progressInput, setProgressInput] = useState(0);
  const [newDependencyId, setNewDependencyId] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [projectTasks, setProjectTasks] = useState([]);

  function load() {
    setStatus("loading");
    client.get(`/tasks/${id}`)
      .then((t) => Promise.all([
        Promise.resolve(t),
        client.get(`/projects/${t.data.projectId}`),
        client.get(`/projects/${t.data.projectId}/members`),
        client.get(`/tasks/${id}/dependencies`),
        client.get(`/projects/${t.data.projectId}/tasks`, { params: { size: 200 } }),
      ]))
      .then(([t, p, m, d, allTasks]) => {
        setTask(t.data);
        setProgressInput(t.data.progress);
        setProject(p.data);
        setMembers(m.data);
        setDependencies(d.data);
        setProjectTasks(allTasks.data.content || []);
        setStatus("success");
      })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load this task.")); setStatus("error"); });
  }

  useEffect(load, [id]);

  const canWrite = task && (isManager || task.ownerId === user?.id);

  async function handleStatusChange(newStatus) {
    setActionError("");
    try {
      const res = await client.patch(`/tasks/${id}/status`, { status: newStatus });
      setTask(res.data);
    } catch (err) {
      setActionError(extractErrorMessage(err, "Unable to update the task status."));
    }
  }

  async function handleProgressSave() {
    setActionError("");
    try {
      const res = await client.patch(`/tasks/${id}/progress`, { progress: Number(progressInput) });
      setTask(res.data);
    } catch (err) {
      setActionError(extractErrorMessage(err, "Unable to update progress."));
    }
  }

  async function handleOwnerChange(ownerId) {
    setActionError("");
    try {
      const res = await client.patch(`/tasks/${id}/owner`, { ownerId: Number(ownerId) });
      setTask(res.data);
    } catch (err) {
      setActionError(extractErrorMessage(err, "Unable to reassign this task."));
    }
  }

  async function handleAddDependency(e) {
    e.preventDefault();
    setActionError("");
    if (!newDependencyId) return;
    try {
      await client.post(`/tasks/${id}/dependencies`, { dependsOnTaskId: Number(newDependencyId) });
      setNewDependencyId("");
      load();
    } catch (err) {
      setActionError(extractErrorMessage(err, "Unable to add this dependency."));
    }
  }

  async function handleRemoveDependency(depId) {
    setActionError("");
    try {
      await client.delete(`/tasks/${id}/dependencies/${depId}`);
      load();
    } catch (err) {
      setActionError(extractErrorMessage(err, "Unable to remove this dependency."));
    }
  }

  async function handleDelete() {
    try {
      await client.delete(`/tasks/${id}`);
      window.location.href = `/projects/${task.projectId}/board`;
    } catch (err) {
      setActionError(extractErrorMessage(err, "Unable to delete this task."));
      setConfirmDelete(false);
    }
  }

  if (status === "loading") return <div className="page"><LoadingState label="Loading task…" /></div>;
  if (status === "error") return <div className="page"><ErrorState message={error} onRetry={load} /></div>;

  const otherProjectTasks = (window.__tf_allTasks || []).filter((t) => t.id !== task.id
    && !dependencies.some((d) => d.dependsOnTaskId === t.id));

  return (
    <div className="page">
      <Breadcrumbs items={[
        { label: "Dashboard", to: "/" },
        { label: "Projects", to: "/projects" },
        { label: project.name, to: `/projects/${project.id}` },
        { label: "Task board", to: `/projects/${project.id}/board` },
        { label: task.title },
      ]} />

      <div className="page__header" style={{ marginTop: 8 }}>
        <div>
          <h1>{task.title}</h1>
          <p className="page__subtitle">In {project.name} · Owner: {task.ownerName || "Unassigned"}</p>
        </div>
        {isManager && (
          <button className="btn btn--danger" onClick={() => setConfirmDelete(true)}>Delete task</button>
        )}
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card__body">
          <div style={{ display: "flex", gap: 8, marginBottom: 10 }}>
            <StatusBadge status={task.status} />
            <PriorityBadge priority={task.priority} />
            {task.overdue && <span className="badge badge--overdue">Overdue</span>}
          </div>
          <p>{task.description || "No description provided."}</p>
          <p>{task.startDate || "No start date"} → {task.dueDate || "No due date"}</p>

          {actionError && <p className="form-error">{actionError}</p>}

          {canWrite && (
            <>
              <div className="form-row" style={{ marginTop: 14 }}>
                <div className="form-field">
                  <label>Status</label>
                  <select value={task.status} onChange={(e) => handleStatusChange(e.target.value)}>
                    {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s.replace("_", " ")}</option>)}
                  </select>
                </div>
                <div className="form-field">
                  <label>Progress ({task.progress}%)</label>
                  <div style={{ display: "flex", gap: 8 }}>
                    <input type="number" min="0" max="100" value={progressInput} onChange={(e) => setProgressInput(e.target.value)} />
                    <button className="btn btn--sm" onClick={handleProgressSave} type="button">Save</button>
                  </div>
                </div>
              </div>
            </>
          )}

          {isManager && (
            <div className="form-field" style={{ maxWidth: 280 }}>
              <label>Reassign owner</label>
              <select value={task.ownerId || ""} onChange={(e) => handleOwnerChange(e.target.value)}>
                <option value="">Unassigned</option>
                {members.map((m) => <option key={m.userId} value={m.userId}>{m.userName}</option>)}
              </select>
            </div>
          )}
        </div>
      </div>

      <div className="section-title">Dependencies</div>
      <p className="page__subtitle" style={{ marginTop: -6, marginBottom: 10 }}>
        This task cannot move to "In progress" until everything below is completed.
      </p>

      {dependencies.length === 0 ? (
        <p>No dependencies.</p>
      ) : (
        <div className="card table-wrap" style={{ marginBottom: 12 }}>
          <table className="data-table">
            <thead><tr><th>Depends on</th><th>Status</th>{isManager && <th></th>}</tr></thead>
            <tbody>
              {dependencies.map((d) => (
                <tr key={d.id}>
                  <td><Link to={`/tasks/${d.dependsOnTaskId}`}>{d.dependsOnTaskTitle}</Link></td>
                  <td><StatusBadge status={d.dependsOnTaskStatus} /></td>
                  {isManager && <td><button className="btn btn--sm btn--ghost" onClick={() => handleRemoveDependency(d.id)}>Remove</button></td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isManager && otherProjectTasks.length > 0 && (
        <form onSubmit={handleAddDependency} style={{ display: "flex", gap: 8 }}>
          <select value={newDependencyId} onChange={(e) => setNewDependencyId(e.target.value)}>
            <option value="">Add a dependency…</option>
            {otherProjectTasks.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
          </select>
          <button className="btn" type="submit" disabled={!newDependencyId}>Add</button>
        </form>
      )}

      <ConfirmDialog
        open={confirmDelete}
        title="Delete task"
        message="This permanently deletes the task and any dependencies referencing it."
        confirmLabel="Delete"
        danger
        onConfirm={handleDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </div>
  );
}
