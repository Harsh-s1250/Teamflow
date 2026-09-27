export default function ErrorState({ message = "Unable to load this page. Please try again.", onRetry }) {
  return (
    <div className="state-block state-block--error">
      <h3>Something went wrong</h3>
      <p style={{ color: "inherit" }}>{message}</p>
      {onRetry && (
        <button className="btn" onClick={onRetry}>Try again</button>
      )}
    </div>
  );
}
