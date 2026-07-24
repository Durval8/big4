import { useMemo, useState } from "react";
import { useTransactions } from "../hooks/useTransactions";
import { TransactionTable } from "../components/transactions/TransactionTable";
import { TransactionFilters, type TransactionFilterValues } from "../components/transactions/TransactionFilters";
import { TransactionFormDrawer } from "../components/transactions/TransactionFormDrawer";
import { DeleteConfirmDialog } from "../components/transactions/DeleteConfirmDialog";
import { Button } from "../components/common/Button";
import type { Transaction } from "../types/transaction";

export function TransactionsPage() {
  const [filters, setFilters] = useState<TransactionFilterValues>({});
  const { transactions, loading, error, create, update, remove } = useTransactions(filters);

  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [deletingTransaction, setDeletingTransaction] = useState<Transaction | null>(null);

  const drawerOpen = isCreating || editingTransaction !== null;
  const activeTransaction = useMemo(() => editingTransaction, [editingTransaction]);

  function closeDrawer() {
    setIsCreating(false);
    setEditingTransaction(null);
  }

  return (
    <div>
      <div className="section-header">
        <h2>Transactions</h2>
        <Button onClick={() => setIsCreating(true)}>Add Transaction</Button>
      </div>
      <div className="card">
        <div style={{ marginBottom: 16 }}>
          <TransactionFilters value={filters} onChange={setFilters} />
        </div>
        {error && <div className="error-banner">{error}</div>}
        {loading ? <p>Loading…</p> : (
          <TransactionTable
            transactions={transactions}
            onEdit={setEditingTransaction}
            onDelete={setDeletingTransaction}
          />
        )}
      </div>

      {drawerOpen && (
        <TransactionFormDrawer
          transaction={activeTransaction}
          onClose={closeDrawer}
          onSubmit={async (input) => {
            if (activeTransaction) {
              await update(activeTransaction.id, input);
            } else {
              await create(input);
            }
          }}
        />
      )}

      {deletingTransaction && (
        <DeleteConfirmDialog
          transaction={deletingTransaction}
          onCancel={() => setDeletingTransaction(null)}
          onConfirm={async () => {
            await remove(deletingTransaction.id);
            setDeletingTransaction(null);
          }}
        />
      )}
    </div>
  );
}
