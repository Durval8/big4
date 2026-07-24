import { useState } from "react";
import type { Transaction } from "../../types/transaction";
import { Button } from "../common/Button";

interface DeleteConfirmDialogProps {
  transaction: Transaction;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}

export function DeleteConfirmDialog({ transaction, onCancel, onConfirm }: DeleteConfirmDialogProps) {
  const [deleting, setDeleting] = useState(false);

  async function handleConfirm() {
    setDeleting(true);
    try {
      await onConfirm();
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h3>Delete transaction?</h3>
        <p>“{transaction.description}” will be permanently removed. This can't be undone.</p>
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleConfirm} disabled={deleting}>
            {deleting ? "Deleting…" : "Delete"}
          </Button>
        </div>
      </div>
    </div>
  );
}
