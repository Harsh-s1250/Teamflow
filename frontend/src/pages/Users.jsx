import { useEffect, useState } from "react";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import EmptyState from "../components/EmptyState";
import Breadcrumbs from "../components/Breadcrumbs";
import ConfirmDialog from "../components/ConfirmDialog";

const emptyForm = { name: "", email: "", role: "TEAM_MEMBER", temporaryPassword: "" };

export default function Users() {
  const [users, setUsers] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");

  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [saving, setSaving] = useState(false);

  const [confirmTarget, setConfirmTarget] = useState(null); // { id, name, nextActive }

  function load() {
    setStatus("loading");
    client.get("/users")
      .then((res) => { setUsers(res.data); setStatus("success"); })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load users.")); setStatus("error"); });
  }

  useEffect(load, []);

  async function handleCreate(e) {
    e.preventDefault();
    setFormError("");
    setSuccessMessage("");
    setSaving(true);
    try {
      await client.post("/users", form);
      setForm(emptyForm);
      setShowForm(false);
      setSuccessMessage("User created successfully.");
      load();
    } catch (err) {
      setFormError(extractErrorMessage(err, "Unable to create this user."));
    } finally {
      setSaving(false);
    }
  }

  async function handleConfirmStatusChange() {
    const { id, nextActive } = confirmTarget;
    setConfirmTarget(null);
    try {
      await client.patch(`/users/${id}/status`, { active: nextActive });
      load();
    } catch (err) {
      setError(extractErrorMessage(err, "Unable to update this user's status."));
    }
  }

  const filtered = users.filter((u) => {
    if (roleFilter !== "ALL" && u.role !== roleFilter) return false;
    if (statusFilter === "ACTIVE" && !u.active) return false;
    if (statusFilter === "INACTIVE" && u.active) return false;
    if (search) {
      const needle = search.toLowerCase();
      if (!u.name.toLowerCase().includes(needle) && !u.email.toLowerCase().includes(needle)) return false;
    }
    return true;
  });

  return (
    <div className="page">
      <Breadcrumbs items={[{ label: "Dashboard", to: "/" }, { label: "Users" }]} />

      <div className="page__header" style={{ marginTop: 8 }}>
        <div>
          <h1>Users</h1>
          <p className="page__subtitle">Create accounts and control who can sign in to TeamFlow.</p>
        </div>
        <button className="btn btn--primary" onClick={() => { setShowForm((s) => !s); setSuccessMessage(""); }}>
          {showForm ? "Cancel" : "+ Add user"}
        </button>
      </div>

      {successMessage && (
        <div className="card" style={{ marginBottom: 16, borderColor: "var(--color-success)" }}>
          <div className="card__body" style={{ color: "var(--color-success)" }}>{successMessage}</div>
        </div>
      )}

      {showForm && (
        <div className="card" style={{ marginBottom: 20 }}>
          <form className="card__body" onSubmit={handleCreate}>
            <h3>Create user</h3>
            <div className="form-row">
              <div className="form-field">
                <label htmlFor="name">Name</label>
                <input id="name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </div>
              <div className="form-field">
                <label htmlFor="email">Email</label>
                <input id="email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
            </div>
            <div className="form-row">
              <div className="form-field">
                <label htmlFor="role">Role</label>
                <select id="role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                  <option value="TEAM_MEMBER">Team member</option>
                  <option value="PROJECT_MANAGER">Project manager</option>
                </select>
              </div>
              <div className="form-field">
                <label htmlFor="temporaryPassword">Temporary password</label>
                <input id="temporaryPassword" type="password" required minLength={8}
                       value={form.temporaryPassword}
                       onChange={(e) => setForm({ ...form, temporaryPassword: e.target.value })} />
              </div>
            </div>
            <p className="page__subtitle" style={{ marginTop: -6 }}>
              At least 8 characters. Share this with the new user directly — TeamFlow does not email it.
            </p>
            {formError && <p className="form-error">{formError}</p>}
            <button className="btn btn--primary" type="submit" disabled={saving}>{saving ? "Creating…" : "Create user"}</button>
          </form>
        </div>
      )}

      <div className="filters-row">
        <input placeholder="Search name or email…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
          <option value="ALL">All roles</option>
          <option value="PROJECT_MANAGER">Project manager</option>
          <option value="TEAM_MEMBER">Team member</option>
        </select>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="ALL">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {status === "loading" && <LoadingState label="Loading users…" />}
      {status === "error" && <ErrorState message={error} onRetry={load} />}
      {status === "success" && filtered.length === 0 && <EmptyState title="No users found." />}
      {status === "success" && filtered.length > 0 && (
        <div className="card table-wrap">
          <table className="data-table">
            <thead>
              <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{u.role === "PROJECT_MANAGER" ? "Project manager" : "Team member"}</td>
                  <td>
                    <span className={`badge ${u.active ? "badge--completed" : "badge--blocked"}`}>
                      {u.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td>
                    <button
                      className="btn btn--sm btn--ghost"
                      onClick={() => setConfirmTarget({ id: u.id, name: u.name, nextActive: !u.active })}
                    >
                      {u.active ? "Deactivate" : "Activate"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={confirmTarget !== null}
        title={confirmTarget?.nextActive ? "Activate user" : "Deactivate user"}
        message={
          confirmTarget?.nextActive
            ? `Activate ${confirmTarget?.name}? They will be able to sign in and receive new assignments again.`
            : `Deactivate ${confirmTarget?.name}? This user will no longer be eligible for new project or task assignments. Their existing tasks and project membership are not changed.`
        }
        confirmLabel={confirmTarget?.nextActive ? "Activate" : "Deactivate"}
        danger={!confirmTarget?.nextActive}
        onConfirm={handleConfirmStatusChange}
        onCancel={() => setConfirmTarget(null)}
      />
    </div>
  );
}
