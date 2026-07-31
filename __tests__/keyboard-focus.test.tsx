import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

describe("keyboard focus smoke", () => {
  it("tabs through filter, toggle, and appearance controls", async () => {
    const user = userEvent.setup();
    render(
      <div>
        <input aria-label="Filter workspaces" />
        <button type="button" aria-label="Show sources">
          Sources
        </button>
        <select aria-label="Appearance preference" defaultValue="system">
          <option value="system">System</option>
          <option value="light">Light</option>
          <option value="dark">Dark</option>
        </select>
      </div>,
    );
    await user.tab();
    expect(screen.getByLabelText("Filter workspaces")).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "Show sources" })).toHaveFocus();
    await user.tab();
    expect(screen.getByLabelText("Appearance preference")).toHaveFocus();
  });
});
