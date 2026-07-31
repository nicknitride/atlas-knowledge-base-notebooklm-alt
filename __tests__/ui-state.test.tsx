import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { EmptyState, ErrorBanner, LoadingRegion } from "@/components/ui-state";

describe("ui-state", () => {
  it("renders empty state with title, description, and primary action", async () => {
    const onAction = vi.fn();
    const user = userEvent.setup();
    render(
      <EmptyState
        title="No workspace selected"
        description="Create a workspace to get started."
        actionLabel="Create workspace"
        onAction={onAction}
      />,
    );
    expect(screen.getByText("No workspace selected")).toBeInTheDocument();
    expect(
      screen.getByText("Create a workspace to get started."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Create workspace" }));
    expect(onAction).toHaveBeenCalledOnce();
  });

  it("renders loading region with accessible status", () => {
    render(<LoadingRegion label="Loading workspaces" />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading workspaces");
  });

  it("renders error banner with retry", async () => {
    const onRetry = vi.fn();
    const user = userEvent.setup();
    render(
      <ErrorBanner
        message="Failed to load"
        onRetry={onRetry}
        onDismiss={() => undefined}
      />,
    );
    expect(screen.getByText("Failed to load")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
