import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import client, { extractErrorMessage } from "../api/client";
import LoadingState from "../components/LoadingState";
import ErrorState from "../components/ErrorState";
import EmptyState from "../components/EmptyState";
import StatusBadge from "../components/StatusBadge";
import PriorityBadge from "../components/PriorityBadge";
import Breadcrumbs from "../components/Breadcrumbs";

export default function Overdue() {
  const [tasks, setTasks] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");

  function load() {
    setStatus("loading");
    client.get("/tasks/overdue")
      .then((res) => { setTasks(res.data); setStatus("success"); })
      .catch((err) => { setError(extractErrorMessage(err, "Unable to load overdue tasks.")); setStatus("error"); });
  }

  useEffect(load, []);

  return (
    <div className="page">
      <Breadcrumbs items={[{ label: "Dashboard", to: "/" }, { label: "Overdue" }]} />
      <div className="page__header" style={{ marginTop: 8 }}>
        <div>
          <h1>Overdue tasks</h1>
          <p className="page__subtitle">
            Tasks whose due date has passed and aren't marked completed. This list is calculated live, not stored.
          </p>
        </div>
      </div>

      {status === "loading" && <LoadingState label="Loading overdue tasks…" />}
      {status === "error" && <ErrorState message={error} onRetry={load} />}
      {status === "success" && tasks.length === 0 && (
        <EmptyState title="No overdue tasks." description="Everything with a due date is either on track or completed." />
      )}
      {status === "success" && tasks.length > 0 && (
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
                  <td style={{ color: "var(--color-danger)", fontWeight: 600 }}>{t.dueDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
