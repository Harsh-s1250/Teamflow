import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const links = [
  { to: "/", label: "Dashboard", exact: true },
  { to: "/projects", label: "Projects" },
  { to: "/overdue", label: "Overdue" },
];

// "Users" is shown to PROJECT_MANAGER only, matching the backend, which
// rejects GET/POST/PATCH on /api/users for a TEAM_MEMBER regardless of
// whether this link is visible - the frontend hides it purely for UX.
const managerLinks = [
  { to: "/users", label: "Users" },
];

export default function Sidebar() {
  const { user, logout, isManager } = useAuth();
  const navLinks = isManager ? [...links, ...managerLinks] : links;

  return (
    <aside className="sidebar">
      <div className="sidebar__brand">
        <span className="sidebar__brand-mark">TF</span>
        TeamFlow
      </div>
      <nav className="sidebar__nav">
        {navLinks.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.exact}
            className={({ isActive }) => "sidebar__link" + (isActive ? " active" : "")}
          >
            {link.label}
          </NavLink>
        ))}
      </nav>
      {user && (
        <div className="sidebar__footer">
          <div className="sidebar__user-name">{user.name}</div>
          <div className="sidebar__user-role">{user.role === "PROJECT_MANAGER" ? "Project manager" : "Team member"}</div>
          <button className="sidebar__logout" onClick={logout}>Log out</button>
        </div>
      )}
    </aside>
  );
}
