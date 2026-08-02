interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <div className="pagination">
      <button
        type="button"
        className="pagination__nav"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        Prev
      </button>
      {Array.from({ length: totalPages }, (_, i) => i).map((n) => (
        <button
          key={n}
          type="button"
          className={`pagination__page${n === page ? " pagination__page--active" : ""}`}
          onClick={() => onPageChange(n)}
        >
          {n + 1}
        </button>
      ))}
      <button
        type="button"
        className="pagination__nav"
        disabled={page === totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
