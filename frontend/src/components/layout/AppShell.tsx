import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useTheme } from "../../hooks/useTheme";

interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const { theme, toggle } = useTheme();

  return (
    <div className="app-shell">
      <nav className="app-nav">
        <span className="app-nav__title">Finance Dashboard</span>
        <div className="app-nav__right">
          <div className="app-nav__links">
            <NavLink
              to="/"
              end
              className={({ isActive }) => `app-nav__link${isActive ? " app-nav__link--active" : ""}`}
            >
              Dashboard
            </NavLink>
            <NavLink
              to="/transactions"
              className={({ isActive }) => `app-nav__link${isActive ? " app-nav__link--active" : ""}`}
            >
              Transactions
            </NavLink>
            <NavLink
              to="/investments"
              className={({ isActive }) => `app-nav__link${isActive ? " app-nav__link--active" : ""}`}
            >
              Investments
            </NavLink>
          </div>
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
      </nav>
      {children}
    </div>
  );
}
