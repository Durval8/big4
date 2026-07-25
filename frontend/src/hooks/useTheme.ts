import { useCallback, useEffect, useState } from "react";

export type Theme = "light" | "dark";

/** The theme index.html already applied to <html data-theme>, falling back to the OS preference. */
function currentTheme(): Theme {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "light" || attr === "dark") {
    return attr;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/** Manual light/dark switch: reflects the choice onto <html data-theme> and persists it. */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => currentTheme());

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    try {
      localStorage.setItem("theme", theme);
    } catch {
      // ignore storage failures (private mode, etc.) — the in-session choice still applies
    }
  }, [theme]);

  const toggle = useCallback(() => {
    setTheme((t) => (t === "dark" ? "light" : "dark"));
  }, []);

  return { theme, toggle };
}
