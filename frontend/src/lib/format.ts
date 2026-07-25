const currencyFormatter = new Intl.NumberFormat(undefined, {
  style: "currency",
  currency: "USD",
});

export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount);
}

export function formatEnumLabel(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/** Signed percentage, e.g. +50.0% / −20.0%; "—" for null (undefined baseline). */
export function formatPercent(pct: number | null): string {
  if (pct == null) return "—";
  const sign = pct > 0 ? "+" : "";
  return `${sign}${pct.toFixed(1)}%`;
}

/** Share quantity: up to 4 decimals, trailing zeros trimmed (e.g. "10", "4.5", "3.3333"). */
export function formatShares(quantity: number): string {
  return Number(quantity.toFixed(4)).toString();
}

/** Price per share to 2 decimals, or "—" when unknown. */
export function formatPrice(price: number | null): string {
  if (price == null) return "—";
  return formatCurrency(price);
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}
