import { useState } from "react";
import { useInvestments } from "../hooks/useInvestments";
import { InvestmentTable } from "../components/investments/InvestmentTable";
import { InvestmentFormDrawer } from "../components/investments/InvestmentFormDrawer";
import { CashOutDialog } from "../components/investments/CashOutDialog";
import { ManualPriceDialog } from "../components/investments/ManualPriceDialog";
import { Button } from "../components/common/Button";
import { formatCurrency, formatPercent } from "../lib/format";
import type { Investment } from "../types/investment";

export function InvestmentsPage() {
  const { investments, summary, loading, error, create, update, cashOut, setPrice } = useInvestments();

  const [isCreating, setIsCreating] = useState(false);
  const [editing, setEditing] = useState<Investment | null>(null);
  const [cashingOut, setCashingOut] = useState<Investment | null>(null);
  const [pricing, setPricing] = useState<Investment | null>(null);

  const drawerOpen = isCreating || editing !== null;
  const pct = summary?.positionChangePct ?? null;
  const pctClass =
    pct == null ? "" : pct > 0 ? "balance-card__value--positive" : pct < 0 ? "balance-card__value--negative" : "";

  return (
    <div>
      <div className="section-header">
        <h2>Investments</h2>
        <Button onClick={() => setIsCreating(true)}>Add Investment</Button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {summary && (
        <div className="balance-grid" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
          <div className="card">
            <div className="balance-card__label">Money Invested</div>
            <div className="balance-card__value">{formatCurrency(summary.totalNetInvested)}</div>
          </div>
          <div className="card">
            <div className="balance-card__label">Current Value</div>
            <div className="balance-card__value">{formatCurrency(summary.totalCurrentValue)}</div>
          </div>
          <div className="card">
            <div className="balance-card__label">Position Change</div>
            <div className={`balance-card__value ${pctClass}`}>{formatPercent(pct)}</div>
          </div>
        </div>
      )}

      <div className="card">
        {loading && investments.length === 0 ? (
          <p>Loading…</p>
        ) : (
          <InvestmentTable
            investments={investments}
            onEdit={setEditing}
            onCashOut={setCashingOut}
            onSetPrice={setPricing}
          />
        )}
      </div>

      {drawerOpen && (
        <InvestmentFormDrawer
          investment={editing}
          onClose={() => {
            setIsCreating(false);
            setEditing(null);
          }}
          onCreate={create}
          onUpdate={update}
        />
      )}

      {cashingOut && (
        <CashOutDialog
          investment={cashingOut}
          onCancel={() => setCashingOut(null)}
          onConfirm={async (amount) => {
            await cashOut(cashingOut.id, amount);
            setCashingOut(null);
          }}
        />
      )}

      {pricing && (
        <ManualPriceDialog
          investment={pricing}
          onCancel={() => setPricing(null)}
          onConfirm={async (price) => {
            await setPrice(pricing.id, price);
            setPricing(null);
          }}
        />
      )}
    </div>
  );
}
