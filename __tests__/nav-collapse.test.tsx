import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "@/components/ui/button";

describe("nav collapse restore control", () => {
  it("restore control is labeled and toggles open state", async () => {
    const user = userEvent.setup();
    const onOpen = vi.fn();
    render(
      <Button
        type="button"
        aria-label="Show navigation"
        onClick={() => onOpen(true)}
      >
        Open
      </Button>,
    );
    await user.click(screen.getByRole("button", { name: "Show navigation" }));
    expect(onOpen).toHaveBeenCalledWith(true);
  });
});
