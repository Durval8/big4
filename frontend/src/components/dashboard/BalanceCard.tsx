import { formatCurrency } from "../../lib/format";

interface BalanceCardProps {
  label: string;
  value: number;
  tone?: "neutral" | "signed";
}

export function BalanceCard({ label, value, tone = "neutral" }: BalanceCardProps) {
  const toneClass =
    tone === "signed" ? (value >= 0 ? " balance-card__value--positive" : " balance-card__value--negative") : "";

  return (
    <div className="card">
      <div className="balance-card__label">{label}</div>
      <div className={`balance-card__value${toneClass}`}>{formatCurrency(value)}</div>
    </div>
  );
}
