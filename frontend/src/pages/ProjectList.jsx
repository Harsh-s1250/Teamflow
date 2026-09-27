import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import EmptyState from "../components/EmptyState";
import StatusBadge from "../components/StatusBadge";
import Breadcrumbs from "../components/Breadcrumbs";
import { useAuth } from "../context/AuthContext";

const emptyForm = { name: "", description: "", startDate: "", endDate: "", status: "PLANNED" };

export default function ProjectList() {
  const { isManager } = useAuth();
  const [projects, setProjects] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [statusFilter, setStatusFilter] = useState("ALL");

  function load() {
    setStatus("loading");
    client.get("/projects", { params: { size: 100 } })
      .then((res) => { setProjects(res.data.content || []); setStatus("success"); })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load projects.")); setStatus("error"); });
  }

  useEffect(load, []);

  async function handleCreate(e) {
    e.preventDefault();
    setFormError("");
    setSaving(true);
    try {
      await client.post("/projects", form);
      setShowForm(false);
      setForm(emptyForm);
      load();
    } catch (err) {
      setFormError(extractErrorMessage(err, "Unable to create the project."));
    } finally {
      setSaving(false);
    }
  }

  const filtered = statusFilter === "ALL" ? projects : projects.filter((p) => p.status === statusFilter);

  return (
    <div className="page">
      <Breadcrumbs items={[{ label: "Dashboard", to: "/" }, { label: "Projects" }]} />
      <div className="page__header" style={{ marginTop: 8 }}>
        <div>
          <h1>Projects</h1>
          <p className="page__subtitle">All projects you have access to.</p>
        </div>
        {isManager && (
          <button className="btn btn--primary" onClick={() => setShowForm((s) => !s)}>
            {showForm ? "Cancel" : "New project"}
          </button>
        )}
      </div>

      {showForm && (
        <div className="card" style={{ marginBottom: 20 }}>
          <form className="card__body" onSubmit={handleCreate}>
            <div className="form-field">
              <label htmlFor="name">Name</label>
              <input id="name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="form-field">
              <label htmlFor="description">Description</label>
              <textarea id="description" rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
            <div className="form-row">
              <div className="form-field">
                <label htmlFor="startDate">Start date</label>
                <input id="startDate" type="date" required value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
              </div>
              <div className="form-field">
                <label htmlFor="endDate">End date</label>
                <input id="endDate" type="date" required value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
              </div>
            </div>
            {formError && <p className="form-error">{formError}</p>}
            <button className="btn btn--primary" type="submit" disabled={saving}>{saving ? "Creating…" : "Create project"}</button>
          </form>
        </div>
      )}

      <div className="filters-row">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="ALL">All statuses</option>
          <option value="PLANNED">Planned</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="ON_HOLD">On hold</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      {status === "loading" && <LoadingState label="Loading projects…" />}
      {status === "error" && <ErrorState message={error} onRetry={load} />}
      {status === "success" && filtered.length === 0 && (
        <EmptyState title="No projects found." description={isManager ? "Create your first project to get started." : "You haven't been added to any projects yet."} />
      )}
      {status === "success" && filtered.length > 0 && (
        <div className="card table-wrap">
          <table className="data-table">
            <thead>
              <tr><th>Name</th><th>Status</th><th>Manager</th><th>Progress</th><th>Dates</th></tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <tr key={p.id}>
                  <td><Link to={`/projects/${p.id}`}>{p.name}</Link></td>
                  <td><StatusBadge status={p.status} /></td>
                  <td>{p.managerName}</td>
                  <td>{p.completedTasks}/{p.totalTasks} tasks ({p.progressPercent}%)</td>
                  <td>{p.startDate} → {p.endDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
