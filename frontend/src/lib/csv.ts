/** Minimal CSV serialisation + download helper (no dependencies). */

export function toCsv(rows: Record<string, unknown>[], columns?: string[]): string {
  if (rows.length === 0) return '';
  const cols = columns ?? Object.keys(rows[0]);
  const cell = (v: unknown): string => {
    if (v === null || v === undefined) return '';
    const s = typeof v === 'number' ? String(v) : String(v);
    // quote cells that could break CSV parsing (commas, quotes, newlines)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const head = cols.join(',');
  const body = rows.map((r) => cols.map((c) => cell(r[c])).join(',')).join('\n');
  return `${head}\n${body}`;
}

/** Trigger a client-side download of a UTF-8 CSV file. */
export function downloadCsv(filename: string, rows: Record<string, unknown>[], columns?: string[]) {
  const csv = toCsv(rows, columns);
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
