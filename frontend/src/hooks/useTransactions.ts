import { useCallback, useEffect, useState } from "react";
import { transactionsApi } from "../api/transactions";
import type { Transaction, TransactionFilters, TransactionInput } from "../types/transaction";

export function useTransactions(filters: TransactionFilters) {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await transactionsApi.list(filters, page);
      setTransactions(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load transactions");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.from, filters.to, filters.accountType, filters.category, filters.sortBy, filters.sortDir, page]);

  useEffect(() => {
    reload();
  }, [reload]);

  // Any filter or sort change goes back to page 0 — a stale page number from a
  // previous, differently-filtered result set wouldn't make sense to keep.
  useEffect(() => {
    setPage(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.from, filters.to, filters.accountType, filters.category, filters.sortBy, filters.sortDir]);

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

  return { transactions, loading, error, page, setPage, totalPages, create, update, remove, reload };
}
