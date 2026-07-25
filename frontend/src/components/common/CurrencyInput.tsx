interface CurrencyInputProps {
  label: string;
  value: number | "";
  onChange: (value: number | "") => void;
  required?: boolean;
  id?: string;
}

export function CurrencyInput({ label, value, onChange, required, id }: CurrencyInputProps) {
  const fieldId = id ?? label.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <input
        id={fieldId}
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
