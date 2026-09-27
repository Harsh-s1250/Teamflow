const CLASS_MAP = { LOW: "low", MEDIUM: "medium", HIGH: "high", CRITICAL: "critical" };
const LABELS = { LOW: "Low", MEDIUM: "Medium", HIGH: "High", CRITICAL: "Critical" };

export default function PriorityBadge({ priority }) {
  const cls = CLASS_MAP[priority] || "medium";
  return <span className={`badge badge--${cls}`}>{LABELS[priority] || priority}</span>;
}
