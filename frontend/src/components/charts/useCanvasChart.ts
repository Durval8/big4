import { useEffect, useRef } from "react";

/** Everything a draw function needs: a scaled 2D context and the canvas size in CSS pixels. */
export interface ChartSurface {
  ctx: CanvasRenderingContext2D;
  width: number;
  height: number;
  /** Resolves a CSS custom property (e.g. "--chart-1") against the live theme. */
  color: (token: string) => string;
}

export type DrawFn = (surface: ChartSurface) => void;

/**
 * Drives a `<canvas>` chart from a draw function.
 *
 * Three things this handles that a plain `useEffect` + `getContext` does not:
 *
 * 1. **Hi-DPI.** The backing store is sized to `width × devicePixelRatio` and the context scaled
 *    to match, so strokes and text render at native density instead of being upscaled. This is
 *    the whole reason these charts moved off SVG — at 1x, hairline strokes and small text were
 *    visibly soft.
 * 2. **Resize.** A `ResizeObserver` on the canvas's parent, not a window resize listener: in a SPA
 *    the card reflows on tab switches, sidebar layout and data-driven height changes without the
 *    window ever resizing.
 * 3. **Theme.** Canvas bakes colors in at draw time — unlike SVG's `fill="var(--token)"`, which
 *    re-resolves on its own. So a flip has to force a redraw, watched here via a `MutationObserver`
 *    on `<html data-theme>` rather than by consuming `useTheme()`: that hook holds *per-component*
 *    `useState`, so the copy inside this hook would never hear about the toggle in `AppShell` and
 *    the charts would keep their old palette until something else re-rendered them. Watching the
 *    attribute also covers a theme changed by anything other than that one button.
 *
 * `draw` is intentionally *not* a dependency: callers pass an inline closure that changes identity
 * every render, which would make the effect re-run on each parent render. A ref keeps the latest
 * closure available without re-subscribing the observer.
 */
export function useCanvasChart(draw: DrawFn, deps: unknown[] = []) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawRef = useRef(draw);

  drawRef.current = draw;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }
    const parent = canvas.parentElement;
    if (!parent) {
      return;
    }

    // Re-read on every render pass, not once per effect: the same computed-style object would
    // keep serving pre-toggle values if it were captured before a theme flip.
    const color = (token: string) => {
      const styles = getComputedStyle(document.documentElement);
      const value = styles.getPropertyValue(token).trim();
      if (value) {
        return value;
      }
      // An undefined custom property resolves to "" — which `addColorStop` rejects by *throwing*,
      // taking the whole React tree down over one bad token name. Falling back keeps a mistyped
      // token to a visibly wrong color instead of a blank page.
      return styles.getPropertyValue("--color-text-primary").trim() || "#000000";
    };

    const render = () => {
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      if (width === 0 || height === 0) {
        return; // not laid out yet — the observer fires again once it is
      }
      const dpr = window.devicePixelRatio || 1;
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);

      const ctx = canvas.getContext("2d");
      if (!ctx) {
        return;
      }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, width, height);
      drawRef.current({ ctx, width, height, color });
    };

    render();

    const resizeObserver = new ResizeObserver(render);
    resizeObserver.observe(parent);

    const themeObserver = new MutationObserver(render);
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-theme"],
    });

    // Covers the no-`data-theme` case, where the OS preference is the live source of truth.
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    media.addEventListener("change", render);

    return () => {
      resizeObserver.disconnect();
      themeObserver.disconnect();
      media.removeEventListener("change", render);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return canvasRef;
}
