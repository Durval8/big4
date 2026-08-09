import { formatCurrency, formatCurrencyCompact } from "../../lib/format";

interface CurrencyValueProps {
  amount: number;
}

/**
 * Renders both the full-precision and abbreviated form of a currency amount and lets CSS pick one
 * per breakpoint (see .currency-value in global.css) — the same "render both, let CSS switch"
 * pattern the mobile table row labels and the chart accessibility fallback tables already use.
 * A resize-driven re-format would work too, but this needs no listener, can't flicker mid-resize,
 * and stays correct through SSR/print/no-JS.
 *
 * Scoped to the header-level stat cards (balance cards, KPI rows, investment summary cards) where
 * a 5-6 digit figure shares a narrow 2-column mobile grid with a label — not to per-row amounts in
 * the transaction/investment tables, which already have a full-width row to themselves and where
 * losing precision (a specific transaction's exact amount) matters more than saving space.
 */
export function CurrencyValue({ amount }: CurrencyValueProps) {
  return (
    <span className="currency-value">
      <span className="currency-value__full">{formatCurrency(amount)}</span>
      <span className="currency-value__compact">{formatCurrencyCompact(amount)}</span>
    </span>
  );
}
