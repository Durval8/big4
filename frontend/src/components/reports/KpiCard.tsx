import type { ReactNode } from "react";

interface KpiCardProps {
  label: string;
  /** A plain string (e.g. a formatted percentage) or a <CurrencyValue> for figures that should
   *  abbreviate on mobile. */
  value: ReactNode;
  /** Tints the value. "neutral" leaves it in the primary text color. */
  tone?: "neutral" | "positive" | "negative";
  hint?: string;
}

/**
 * One headline figure with its name underneath — the row of these across the top of Reports is
 * the summary layer people read before any chart. The label sits *below* the value because the
 * value is what's being scanned; the label only needs to resolve the ambiguity afterwards.
 */
export function KpiCard({ label, value, tone = "neutral", hint }: KpiCardProps) {
  const color =
    tone === "positive"
      ? "var(--color-positive)"
      : tone === "negative"
        ? "var(--color-negative)"
        : "var(--color-text-primary)";

  return (
    <div className="kpi-card">
      <span className="kpi-card__value" style={{ color }}>
        {value}
      </span>
      <span className="kpi-card__label">{label}</span>
      {hint && <span className="kpi-card__hint">{hint}</span>}
    </div>
  );
}
