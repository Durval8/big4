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
      <td>{formatDate(transaction.transactionDate)}</td>
      <td>{transaction.description}</td>
      <td>
        <span className="pill">
          {transaction.category ? formatEnumLabel(transaction.category) : formatEnumLabel(transaction.transactionType)}
        </span>
      </td>
      <td>{secondaryTag}</td>
      <td className={amountClass}>
        {sign}
        {formatCurrency(transaction.amount)}
      </td>
      <td>
        <div className="row-actions">
          <button onClick={() => onEdit(transaction)}>Edit</button>
          <button className="danger" onClick={() => onDelete(transaction)}>
            Delete
          </button>
        </div>
      </td>
    </tr>
  );
}
