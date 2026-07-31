import { describe, expect, it } from "vitest";
import {
  APPEARANCE_STORAGE_KEY,
  getAppearance,
  setAppearance,
} from "@/lib/appearance";

describe("appearance control persistence", () => {
  it("persists selected mode across reload simulation", () => {
    setAppearance("dark");
    expect(localStorage.getItem(APPEARANCE_STORAGE_KEY)).toBe("dark");
    // Simulate reload by re-reading storage only
    expect(getAppearance()).toBe("dark");
    setAppearance("light");
    expect(getAppearance()).toBe("light");
  });
});
