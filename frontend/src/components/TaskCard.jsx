import { Link } from "react-router-dom";
import PriorityBadge from "./PriorityBadge";

export default function TaskCard({ task }) {
  return (
    <Link to={`/tasks/${task.id}`} className="task-card" style={{ display: "block" }}>
      <div className="task-card__title">{task.title}</div>
      <div className="task-card__meta">
        <span>{task.ownerName || "Unassigned"}</span>
        <PriorityBadge priority={task.priority} />
      </div>
      <div className="task-card__meta" style={{ marginTop: 6 }}>
        <span>{task.dueDate ? `Due ${task.dueDate}` : "No due date"}</span>
        {task.overdue && <span style={{ color: "var(--color-danger)", fontWeight: 600 }}>Overdue</span>}
      </div>
      <div className="task-card__progress-track">
        <div className="task-card__progress-fill" style={{ width: `${task.progress}%` }} />
      </div>
    </Link>
  );
}
