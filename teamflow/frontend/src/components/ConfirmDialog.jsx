export default function ConfirmDialog({ open, title, message, confirmLabel = "Confirm", danger, onConfirm, onCancel }) {
  if (!open) return null;
  return (
    <div className="overlay" role="dialog" aria-modal="true">
      <div className="card dialog">
        <h3>{title}</h3>
        <p>{message}</p>
        <div className="dialog__actions">
          <button className="btn" onClick={onCancel}>Cancel</button>
          <button className={danger ? "btn btn--danger" : "btn btn--primary"} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
