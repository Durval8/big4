import type { Transaction } from "../../types/transaction";
import { formatCurrency, formatDate, formatEnumLabel } from "../../lib/format";

interface TransactionRowProps {
  transaction: Transaction;
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
}

function amountSign(transaction: Transaction): "+" | "-" {
  return transaction.transactionType === "EXPENSE" || transaction.transactionType === "TRANSFER" ? "-" : "+";
}

export function TransactionRow({ transaction, onEdit, onDelete }: TransactionRowProps) {
  const sign = amountSign(transaction);
  const amountClass = sign === "-" ? "amount--negative" : "amount--positive";
  const secondaryTag =
    transaction.transactionType === "TRANSFER"
      ? `${formatEnumLabel(transaction.accountType)} → ${formatEnumLabel(transaction.linkedAccountType ?? "")}`
      : formatEnumLabel(transaction.accountType);

  return (
    <tr>
      <td data-label="Date">{formatDate(transaction.transactionDate)}</td>
      <td data-label="Description">{transaction.description}</td>
      <td data-label="Category">
        <span className="pill">
          {transaction.category ? formatEnumLabel(transaction.category) : formatEnumLabel(transaction.transactionType)}
        </span>
      </td>
      <td data-label="Account">{secondaryTag}</td>
      <td data-label="Amount" className={amountClass}>
        {sign}
        {formatCurrency(transaction.amount)}
      </td>
      <td className="transaction-table__actions-cell">
        {transaction.sourceEventId ? (
          // Generated from an investment buy/cash-out. The API rejects edits and deletes on these
          // (the investments service still holds the position), so don't offer the action at all.
          <span className="row-actions__locked" title="Generated from an investment — manage it on the Investments page">
            From investments
          </span>
        ) : (
          <div className="row-actions">
            <button onClick={() => onEdit(transaction)}>Edit</button>
            <button className="danger" onClick={() => onDelete(transaction)}>
              Delete
            </button>
          </div>
        )}
      </td>
    </tr>
  );
}
