import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import EmptyState from "../components/EmptyState";
import StatusBadge from "../components/StatusBadge";
import Breadcrumbs from "../components/Breadcrumbs";
import ConfirmDialog from "../components/ConfirmDialog";
import { useAuth } from "../context/AuthContext";

export default function ProjectDetails() {
  const { id } = useParams();
  const { isManager } = useAuth();
  const navigate = useNavigate();

  const [project, setProject] = useState(null);
  const [members, setMembers] = useState([]);
  const [users, setUsers] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");

  const [selectedUserId, setSelectedUserId] = useState("");
  const [memberError, setMemberError] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmRemoveUserId, setConfirmRemoveUserId] = useState(null);

  function load() {
    setStatus("loading");
    Promise.all([
      client.get(`/projects/${id}`),
      client.get(`/projects/${id}/members`),
      isManager ? client.get("/users") : Promise.resolve({ data: [] }),
    ])
      .then(([p, m, u]) => {
        setProject(p.data);
        setMembers(m.data);
        setUsers(u.data);
        setStatus("success");
      })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load this project.")); setStatus("error"); });
  }

  useEffect(load, [id]);

  async function handleAddMember(e) {
    e.preventDefault();
    setMemberError("");
    if (!selectedUserId) return;
    try {
      await client.post(`/projects/${id}/members`, { userId: Number(selectedUserId) });
      setSelectedUserId("");
      load();
    } catch (err) {
      setMemberError(extractErrorMessage(err, "Unable to add this member."));
    }
  }

  async function handleRemoveMember() {
    try {
      await client.delete(`/projects/${id}/members/${confirmRemoveUserId}`);
      setConfirmRemoveUserId(null);
      load();
    } catch (err) {
      setMemberError(extractErrorMessage(err, "Unable to remove this member."));
      setConfirmRemoveUserId(null);
    }
  }

  async function handleDeleteProject() {
    try {
      await client.delete(`/projects/${id}`);
      navigate("/projects");
    } catch (err) {
      setError(extractErrorMessage(err, "Unable to delete this project."));
      setConfirmDelete(false);
    }
  }

  async function handleStatusChange(newStatus) {
    try {
      await client.put(`/projects/${id}`, {
        name: project.name, description: project.description,
        startDate: project.startDate, endDate: project.endDate, status: newStatus,
      });
      load();
    } catch (err) {
      setError(extractErrorMessage(err, "Unable to update the project status."));
    }
  }

  if (status === "loading") return <div className="page"><LoadingState label="Loading project…" /></div>;
  if (status === "error") return <div className="page"><ErrorState message={error} onRetry={load} /></div>;

  const memberIds = new Set(members.map((m) => m.userId));
  const availableUsers = users.filter((u) => u.active && !memberIds.has(u.id));

  return (
    <div className="page">
      <Breadcrumbs items={[{ label: "Dashboard", to: "/" }, { label: "Projects", to: "/projects" }, { label: project.name }]} />

      <div className="page__header" style={{ marginTop: 8 }}>
        <div>
          <h1>{project.name}</h1>
          <p className="page__subtitle">Managed by {project.managerName} · {project.startDate} → {project.endDate}</p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Link className="btn btn--primary" to={`/projects/${id}/board`}>Open task board</Link>
          {isManager && (
            <button className="btn btn--danger" onClick={() => setConfirmDelete(true)}>Delete project</button>
          )}
        </div>
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card__body">
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
            <StatusBadge status={project.status} />
            {isManager && (
              <select value={project.status} onChange={(e) => handleStatusChange(e.target.value)}>
                {["PLANNED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "CANCELLED"].map((s) => (
                  <option key={s} value={s}>{s.replace("_", " ")}</option>
                ))}
              </select>
            )}
          </div>
          <p>{project.description || "No description provided."}</p>
          <p>Progress: {project.completedTasks}/{project.totalTasks} tasks completed ({project.progressPercent}%)</p>
        </div>
      </div>

      <div className="section-title">Members</div>
      {isManager && (
        <form onSubmit={handleAddMember} style={{ display: "flex", gap: 8, marginBottom: 12 }}>
          <select value={selectedUserId} onChange={(e) => setSelectedUserId(e.target.value)}>
            <option value="">Add a member…</option>
            {availableUsers.map((u) => (
              <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
            ))}
          </select>
          <button className="btn" type="submit" disabled={!selectedUserId}>Add</button>
        </form>
      )}
      {memberError && <p className="form-error">{memberError}</p>}

      {members.length === 0 ? (
        <EmptyState title="No members yet." />
      ) : (
        <div className="card table-wrap" style={{ marginBottom: 22 }}>
          <table className="data-table">
            <thead><tr><th>Name</th><th>Email</th><th>Role</th>{isManager && <th></th>}</tr></thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.id}>
                  <td>{m.userName}</td>
                  <td>{m.userEmail}</td>
                  <td>{m.role === "PROJECT_MANAGER" ? "Project manager" : "Team member"}</td>
                  {isManager && (
                    <td>
                      {m.userId !== project.managerId && (
                        <button className="btn btn--sm btn--ghost" onClick={() => setConfirmRemoveUserId(m.userId)}>Remove</button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={confirmDelete}
        title="Delete project"
        message="This permanently deletes the project and all of its tasks. This cannot be undone."
        confirmLabel="Delete"
        danger
        onConfirm={handleDeleteProject}
        onCancel={() => setConfirmDelete(false)}
      />
      <ConfirmDialog
        open={confirmRemoveUserId !== null}
        title="Remove member"
        message="This member will lose access to the project."
        confirmLabel="Remove"
        danger
        onConfirm={handleRemoveMember}
        onCancel={() => setConfirmRemoveUserId(null)}
      />
    </div>
  );
}
