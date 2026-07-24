import type { InputHTMLAttributes } from "react";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
}

export function TextField({ label, id, ...rest }: TextFieldProps) {
  const fieldId = id ?? label.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <input id={fieldId} {...rest} />
    </div>
  );
}
