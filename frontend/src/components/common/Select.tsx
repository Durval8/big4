interface Option {
  value: string;
  label: string;
}

interface SelectProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: Option[];
  placeholder?: string;
  required?: boolean;
}

export function Select({ label, value, onChange, options, placeholder, required }: SelectProps) {
  const fieldId = label.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <select id={fieldId} value={value} required={required} onChange={(e) => onChange(e.target.value)}>
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
}
