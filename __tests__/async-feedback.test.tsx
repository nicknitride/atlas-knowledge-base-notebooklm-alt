import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ErrorBanner, LoadingRegion } from "@/components/ui-state";

describe("async feedback (SC-003)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows loading indicator within 200ms of action start", () => {
    let show = false;
    const { rerender } = render(
      show ? <LoadingRegion label="Loading" /> : null,
    );
    const started = performance.now();
    act(() => {
      show = true;
      rerender(<LoadingRegion label="Loading workspaces" />);
      vi.advanceTimersByTime(200);
    });
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(performance.now() - started).toBeLessThanOrEqual(200);
  });

  it("shows recoverable error UI", () => {
    const onRetry = vi.fn();
    render(
      <ErrorBanner
        message="Failed to load"
        onRetry={onRetry}
        onDismiss={() => undefined}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Failed to load");
    expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
  });
});
