import { useCallback, useEffect, useState } from "react";
import { investmentsApi } from "../api/investments";
import type {
  CashOutInput,
  Investment,
  InvestmentCorrectionInput,
  InvestmentInput,
  InvestmentSummary,
} from "../types/investment";

export function useInvestments() {
  const [investments, setInvestments] = useState<Investment[]>([]);
  const [summary, setSummary] = useState<InvestmentSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [list, sum] = await Promise.all([investmentsApi.list(), investmentsApi.summary()]);
      setInvestments(list);
      setSummary(sum);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load investments");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const create = useCallback(
    async (input: InvestmentInput) => {
      await investmentsApi.create(input);
      await reload();
    },
    [reload],
  );

  const update = useCallback(
    async (id: string, input: InvestmentCorrectionInput) => {
      await investmentsApi.update(id, input);
      await reload();
    },
    [reload],
  );

  const cashOut = useCallback(
    async (id: string, input: CashOutInput) => {
      await investmentsApi.cashOut(id, input);
      await reload();
    },
    [reload],
  );

  const setPrice = useCallback(
    async (id: string, price: number) => {
      await investmentsApi.setPrice(id, price);
      await reload();
    },
    [reload],
  );

  const remove = useCallback(
    async (id: string) => {
      await investmentsApi.remove(id);
      await reload();
    },
    [reload],
  );

  return { investments, summary, loading, error, create, update, cashOut, setPrice, remove, reload };
}
