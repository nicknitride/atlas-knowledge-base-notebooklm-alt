import { describe, expect, it } from "vitest";
import { computeSourcesVisible } from "@/lib/sources-visibility";

describe("computeSourcesVisible", () => {
  it("hides when no citations and not forced open", () => {
    expect(
      computeSourcesVisible({
        citationCount: 0,
        sourcesForcedOpen: false,
        sourcesUserCollapsed: false,
      }),
    ).toBe(false);
  });

  it("shows calm-empty path when forced open with zero citations", () => {
    expect(
      computeSourcesVisible({
        citationCount: 0,
        sourcesForcedOpen: true,
        sourcesUserCollapsed: false,
      }),
    ).toBe(true);
  });

  it("shows when citations exist and not user-collapsed", () => {
    expect(
      computeSourcesVisible({
        citationCount: 2,
        sourcesForcedOpen: false,
        sourcesUserCollapsed: false,
      }),
    ).toBe(true);
  });

  it("hides when user collapsed even with citations", () => {
    expect(
      computeSourcesVisible({
        citationCount: 2,
        sourcesForcedOpen: false,
        sourcesUserCollapsed: true,
      }),
    ).toBe(false);
  });
});
