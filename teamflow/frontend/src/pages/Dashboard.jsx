import { useEffect, useState } from "react";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import EmptyState from "../components/EmptyState";
import StatusBadge from "../components/StatusBadge";
import PriorityBadge from "../components/PriorityBadge";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function Dashboard() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");

  function load() {
    setStatus("loading");
    client.get("/dashboard")
      .then((res) => { setData(res.data); setStatus("success"); })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load the dashboard.")); setStatus("error"); });
  }

  useEffect(load, []);

  if (status === "loading") return <div className="page"><LoadingState label="Loading dashboard…" /></div>;
  if (status === "error") return <div className="page"><ErrorState message={error} onRetry={load} /></div>;

  const { projects, tasks, overdueTasks, blockedTasks, highPriorityTasks } = data;

  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1>Dashboard</h1>
          <p className="page__subtitle">Welcome back, {user?.name}. Here's what's happening across your projects.</p>
        </div>
      </div>

      <div className="section-title">Projects</div>
      <div className="stat-grid">
        <Stat label="Total" value={projects.total} />
        <Stat label="Planned" value={projects.planned} />
        <Stat label="Active" value={projects.active} />
        <Stat label="On hold" value={projects.onHold} />
        <Stat label="Completed" value={projects.completed} />
      </div>

      <div className="section-title">Tasks</div>
      <div className="stat-grid">
        <Stat label="Total" value={tasks.total} />
        <Stat label="To do" value={tasks.todo} />
        <Stat label="In progress" value={tasks.inProgress} />
        <Stat label="Blocked" value={tasks.blocked} />
        <Stat label="Completed" value={tasks.completed} />
        <Stat label="Overdue" value={tasks.overdue} highlight={tasks.overdue > 0} />
      </div>

      <TaskListSection title="Overdue tasks" tasks={overdueTasks} empty="No overdue tasks. Nice work." />
      <TaskListSection title="Blocked tasks" tasks={blockedTasks} empty="No blocked tasks right now." />
      <TaskListSection title="High & critical priority" tasks={highPriorityTasks} empty="No high or critical priority tasks." />
    </div>
  );
}

function Stat({ label, value, highlight }) {
  return (
    <div className="card stat-card">
      <div className="stat-card__value" style={highlight ? { color: "var(--color-danger)" } : undefined}>{value}</div>
      <div className="stat-card__label">{label}</div>
    </div>
  );
}

function TaskListSection({ title, tasks, empty }) {
  return (
    <>
      <div className="section-title">{title}</div>
      {tasks.length === 0 ? (
        <EmptyState title={empty} />
      ) : (
        <div className="card table-wrap">
          <table className="data-table">
            <thead>
              <tr><th>Task</th><th>Owner</th><th>Status</th><th>Priority</th><th>Due date</th></tr>
            </thead>
            <tbody>
              {tasks.map((t) => (
                <tr key={t.id}>
                  <td><Link to={`/tasks/${t.id}`}>{t.title}</Link></td>
                  <td>{t.ownerName || "Unassigned"}</td>
                  <td><StatusBadge status={t.status} /></td>
                  <td><PriorityBadge priority={t.priority} /></td>
                  <td>{t.dueDate || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
