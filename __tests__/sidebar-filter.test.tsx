import { describe, expect, it } from "vitest";
import { filterByName } from "@/lib/list-filter";

describe("sidebar filter UI binding helper", () => {
  const conversations = [
    { id: "1", title: "Q1 Planning" },
    { id: "2", title: "Vendor notes" },
  ];

  it("filters conversations by title and supports clear via empty query", () => {
    const filtered = filterByName(conversations, "vendor", (c) => c.title);
    expect(filtered.items).toHaveLength(1);
    expect(filtered.items[0].id).toBe("2");
    const cleared = filterByName(conversations, "", (c) => c.title);
    expect(cleared.items).toHaveLength(2);
    expect(cleared.emptyReason).toBe("none");
  });

  it("exposes no-matches for empty filter results", () => {
    const filtered = filterByName(conversations, "zzz", (c) => c.title);
    expect(filtered.emptyReason).toBe("no-matches");
  });
});
