import { TIME_RANGES, type TimeRange } from "../../types/transaction";
import { formatEnumLabel } from "../../lib/format";

interface TimeRangeSelectorProps {
  value: TimeRange;
  onChange: (range: TimeRange) => void;
}

export function TimeRangeSelector({ value, onChange }: TimeRangeSelectorProps) {
  return (
    <div className="time-range-selector">
      {TIME_RANGES.map((range) => (
        <button
          key={range}
          type="button"
          className={`time-range-selector__option${range === value ? " time-range-selector__option--active" : ""}`}
          onClick={() => onChange(range)}
        >
          {range === "ALL" ? "All time" : `Last ${formatEnumLabel(range).toLowerCase()}`}
        </button>
      ))}
    </div>
  );
}
