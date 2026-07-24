import { useCallback, useEffect, useState } from "react";
import { budgetsApi } from "../api/budgets";
import type { BudgetInput, BudgetProgress } from "../types/budget";
import type { TimeRange } from "../types/transaction";

/**
 * Loads budget progress for the given time range and exposes CRUD that reloads
 * afterwards, so the Dashboard's budget section always reflects the current window.
 */
export function useBudgets(range: TimeRange) {
  const [budgets, setBudgets] = useState<BudgetProgress[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBudgets(await budgetsApi.progress(range));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load budgets");
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    reload();
  }, [reload]);

  const create = useCallback(
    async (input: BudgetInput) => {
      await budgetsApi.create(input);
      await reload();
    },
    [reload],
  );

  const update = useCallback(
    async (id: number, input: BudgetInput) => {
      await budgetsApi.update(id, input);
      await reload();
    },
    [reload],
  );

  const remove = useCallback(
    async (id: number) => {
      await budgetsApi.remove(id);
      await reload();
    },
    [reload],
  );

  return { budgets, loading, error, create, update, remove, reload };
}
