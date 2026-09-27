const LABELS = {
  TODO: "To do",
  IN_PROGRESS: "In progress",
  BLOCKED: "Blocked",
  COMPLETED: "Completed",
  PLANNED: "Planned",
  ON_HOLD: "On hold",
  CANCELLED: "Cancelled",
};

const CLASS_MAP = {
  TODO: "todo",
  IN_PROGRESS: "in-progress",
  BLOCKED: "blocked",
  COMPLETED: "completed",
  PLANNED: "planned",
  ON_HOLD: "on-hold",
  CANCELLED: "cancelled",
};

export default function StatusBadge({ status }) {
  const cls = CLASS_MAP[status] || "todo";
  return <span className={`badge badge--${cls}`}>{LABELS[status] || status}</span>;
}
