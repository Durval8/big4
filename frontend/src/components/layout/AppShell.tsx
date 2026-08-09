import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useTheme } from "../../hooks/useTheme";

interface AppShellProps {
  children: ReactNode;
}

/** Inline SVG so the icons inherit `currentColor` and need no font or network request. */
const ICONS: Record<string, string> = {
  dashboard: "M4 13h7V4H4v9Zm0 7h7v-5H4v5Zm9 0h7v-9h-7v9Zm0-16v5h7V4h-7Z",
  transactions: "M4 6h16v2H4V6Zm0 5h16v2H4v-2Zm0 5h10v2H4v-2Z",
  reports: "M5 20V10h3v10H5Zm5.5 0V4h3v16h-3ZM16 20v-6h3v6h-3Z",
  investments: "M4 18l5-6 4 3 6-8 1.6 1.2L13 18l-4-3-3.4 4.2L4 18Z",
};

const LINKS = [
  { to: "/", label: "Dashboard", icon: "dashboard", end: true },
  { to: "/transactions", label: "Transactions", icon: "transactions", end: false },
  { to: "/reports", label: "Reports", icon: "reports", end: false },
  { to: "/investments", label: "Investments", icon: "investments", end: false },
];

/**
 * A persistent left rail with the page area beside it. The rail collapses to a horizontal strip
 * under the 800px breakpoint the rest of the app already uses — a fixed sidebar at phone widths
 * would eat most of the viewport.
 */
export function AppShell({ children }: AppShellProps) {
  const { theme, toggle } = useTheme();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <span className="sidebar__mark" aria-hidden="true">
            ◆
          </span>
          <span className="sidebar__title">Finance</span>
        </div>

        <nav className="sidebar__nav" aria-label="Main">
          {LINKS.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) => `sidebar__link${isActive ? " sidebar__link--active" : ""}`}
            >
              <svg className="sidebar__icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d={ICONS[link.icon]} fill="currentColor" />
              </svg>
              <span>{link.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__footer">
          <a
            className="sidebar__link sidebar__link--muted"
            href="https://github.com/Durval8/big4"
            target="_blank"
            rel="noreferrer noopener"
          >
            <span>Docs</span>
          </a>
          <button
            type="button"
            className="theme-toggle"
            onClick={toggle}
            aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
            title={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
          >
            {theme === "dark" ? "☀️" : "🌙"}
          </button>
        </div>
      </aside>

      <main className="app-main">{children}</main>
    </div>
  );
}
