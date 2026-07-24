import { useCallback, useEffect, useState } from "react";
import { transactionsApi } from "../api/transactions";
import type { Transaction, TransactionFilters, TransactionInput } from "../types/transaction";

export function useTransactions(filters: TransactionFilters) {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await transactionsApi.list(filters);
      setTransactions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load transactions");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.from, filters.to, filters.accountType, filters.category]);

  useEffect(() => {
    reload();
  }, [reload]);

  const create = useCallback(
    async (input: TransactionInput) => {
      await transactionsApi.create(input);
      await reload();
    },
    [reload],
  );

  const update = useCallback(
    async (id: number, input: TransactionInput) => {
      await transactionsApi.update(id, input);
      await reload();
    },
    [reload],
  );

  const remove = useCallback(
    async (id: number) => {
      await transactionsApi.remove(id);
      await reload();
    },
    [reload],
  );

  return { transactions, loading, error, create, update, remove, reload };
}
