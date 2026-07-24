interface CurrencyInputProps {
  label: string;
  value: number | "";
  onChange: (value: number | "") => void;
  required?: boolean;
}

export function CurrencyInput({ label, value, onChange, required }: CurrencyInputProps) {
  return (
    <div className="field">
      <label htmlFor="amount">{label}</label>
      <input
        id="amount"
        type="number"
        step="0.01"
        min="0.01"
        inputMode="decimal"
        required={required}
        value={value}
        onChange={(e) => onChange(e.target.value === "" ? "" : Number(e.target.value))}
      />
    </div>
  );
}
