export type FilterEmptyReason = "none" | "no-data" | "no-matches";

export interface FilterByNameResult<T> {
  query: string;
  items: T[];
  emptyReason: FilterEmptyReason;
}

export function filterByName<T>(
  source: T[],
  query: string,
  getLabel: (item: T) => string = (item) =>
    String((item as { name?: string; title?: string }).name ??
      (item as { title?: string }).title ??
      ""),
): FilterByNameResult<T> {
  const trimmed = query.trim();
  if (source.length === 0 && trimmed.length === 0) {
    return { query: trimmed, items: [], emptyReason: "no-data" };
  }
  if (trimmed.length === 0) {
    return { query: trimmed, items: source, emptyReason: "none" };
  }
  const needle = trimmed.toLowerCase();
  const items = source.filter((item) =>
    getLabel(item).toLowerCase().includes(needle),
  );
  return {
    query: trimmed,
    items,
    emptyReason: items.length === 0 ? "no-matches" : "none",
  };
}
