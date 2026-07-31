import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { EmptyState } from "@/components/ui-state";

describe("empty state CTAs (contracts/ui-shell)", () => {
  it("no-workspace CTA is Create workspace", () => {
    const onAction = vi.fn();
    render(
      <EmptyState
        title="No workspace selected"
        description="Create a workspace to organize sources and start grounded chat."
        actionLabel="Create workspace"
        onAction={onAction}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Create workspace" }),
    ).toBeInTheDocument();
  });

  it("no-conversations CTA is Start conversation", () => {
    render(
      <EmptyState
        title="No conversations yet"
        description="Start a conversation to ask grounded questions."
        actionLabel="Start conversation"
        onAction={() => undefined}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Start conversation" }),
    ).toBeInTheDocument();
  });

  it("no-documents CTA is Upload source", () => {
    render(
      <EmptyState
        title="No documents yet"
        description="Upload a source to ground answers in this workspace."
        actionLabel="Upload source"
        onAction={() => undefined}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Upload source" }),
    ).toBeInTheDocument();
  });
});
