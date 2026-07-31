import { describe, expect, it } from "vitest";
import { filterByName } from "@/lib/list-filter";

describe("filterByName", () => {
  const items = [
    { id: "1", name: "Alpha Notes" },
    { id: "2", name: "beta research" },
    { id: "3", name: "Gamma" },
  ];

  it("matches case-insensitively by substring", () => {
    expect(filterByName(items, "alpha").items.map((i) => i.id)).toEqual(["1"]);
    expect(filterByName(items, "BETA").items.map((i) => i.id)).toEqual(["2"]);
  });

  it("returns all items when query is empty or whitespace", () => {
    expect(filterByName(items, "").items).toHaveLength(3);
    expect(filterByName(items, "   ").emptyReason).toBe("none");
  });

  it("returns no-matches when query has no hits", () => {
    const result = filterByName(items, "zzz");
    expect(result.items).toHaveLength(0);
    expect(result.emptyReason).toBe("no-matches");
  });

  it("returns no-data when source list is empty and query empty", () => {
    expect(filterByName([], "").emptyReason).toBe("no-data");
  });
});
