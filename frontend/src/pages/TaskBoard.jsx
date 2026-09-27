import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import Breadcrumbs from "../components/Breadcrumbs";
import TaskCard from "../components/TaskCard";
import { useAuth } from "../context/AuthContext";

const COLUMNS = [
  { key: "TODO", label: "To do" },
  { key: "IN_PROGRESS", label: "In progress" },
  { key: "BLOCKED", label: "Blocked" },
  { key: "COMPLETED", label: "Completed" },
];

const emptyForm = { title: "", description: "", priority: "MEDIUM", ownerId: "", startDate: "", dueDate: "" };

export default function TaskBoard() {
  const { id } = useParams();
  const { isManager } = useAuth();

  const [project, setProject] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [members, setMembers] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  function load() {
    setStatus("loading");
    Promise.all([
      client.get(`/projects/${id}`),
      client.get(`/projects/${id}/tasks`, { params: { size: 200 } }),
      client.get(`/projects/${id}/members`),
    ])
      .then(([p, t, m]) => {
        setProject(p.data);
        setTasks(t.data.content || []);
        setMembers(m.data);
        setStatus("success");
      })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load the task board.")); setStatus("error"); });
  }

  useEffect(load, [id]);

  async function handleCreate(e) {
    e.preventDefault();
    setFormError("");
    setSaving(true);
    try {
      await client.post(`/projects/${id}/tasks`, {
        ...form,
        ownerId: form.ownerId ? Number(form.ownerId) : null,
        startDate: form.startDate || null,
        dueDate: form.dueDate || null,
      });
      setForm(emptyForm);
      setShowForm(false);
      load();
    } catch (err) {
      setFormError(extractErrorMessage(err, "Unable to create the task."));
    } finally {
      setSaving(false);
    }
  }

  if (status === "loading") return <div className="page"><LoadingState label="Loading task board…" /></div>;
  if (status === "error") return <div className="page"><ErrorState message={error} onRetry={load} /></div>;

  return (
    <div className="page">
      <Breadcrumbs items={[{ label: "Dashboard", to: "/" }, { label: "Projects", to: "/projects" }, { label: project.name, to: `/projects/${id}` }, { label: "Task board" }]} />

      <div className="page__header" style={{ marginTop: 8 }}>
        <div>
          <h1>{project.name} — task board</h1>
          <p className="page__subtitle">Drag isn't required here — open a task to change its status.</p>
        </div>
        {isManager && (
          <button className="btn btn--primary" onClick={() => setShowForm((s) => !s)}>
            {showForm ? "Cancel" : "New task"}
          </button>
        )}
      </div>

      {showForm && (
        <div className="card" style={{ marginBottom: 20 }}>
          <form className="card__body" onSubmit={handleCreate}>
            <div className="form-field">
              <label htmlFor="title">Title</label>
              <input id="title" required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </div>
            <div className="form-field">
              <label htmlFor="description">Description</label>
              <textarea id="description" rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
            <div className="form-row">
              <div className="form-field">
                <label htmlFor="owner">Owner</label>
                <select id="owner" value={form.ownerId} onChange={(e) => setForm({ ...form, ownerId: e.target.value })}>
                  <option value="">Unassigned</option>
                  {members.map((m) => <option key={m.userId} value={m.userId}>{m.userName}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="priority">Priority</label>
                <select id="priority" value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>
                  {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </div>
            </div>
            <div className="form-row">
              <div className="form-field">
                <label htmlFor="startDate">Start date</label>
                <input id="startDate" type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
              </div>
              <div className="form-field">
                <label htmlFor="dueDate">Due date</label>
                <input id="dueDate" type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
              </div>
            </div>
            {formError && <p className="form-error">{formError}</p>}
            <button className="btn btn--primary" type="submit" disabled={saving}>{saving ? "Creating…" : "Create task"}</button>
          </form>
        </div>
      )}

      <div className="board">
        {COLUMNS.map((col) => {
          const colTasks = tasks.filter((t) => t.status === col.key);
          return (
            <div className="board-column" key={col.key}>
              <div className="board-column__header">
                <span className="board-column__title">{col.label}</span>
                <span className="board-column__count">{colTasks.length}</span>
              </div>
              {colTasks.length === 0 && (
                <p style={{ fontSize: "0.8rem", padding: "0 6px" }}>No tasks here.</p>
              )}
              {colTasks.map((t) => <TaskCard key={t.id} task={t} />)}
            </div>
          );
        })}
      </div>
    </div>
  );
}
