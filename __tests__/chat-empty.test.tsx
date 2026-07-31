import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ChatPanel from "@/components/chat-panel";

vi.mock("@/lib/api", () => ({
  fetchConversationDetail: vi.fn(),
  createConversation: vi.fn(),
  streamChatMessage: vi.fn(),
}));

describe("ChatPanel empty primary action", () => {
  it("shows create workspace when no workspace selected", () => {
    render(
      <ChatPanel
        workspaceId={null}
        conversationId={null}
        onConversationCreated={() => undefined}
        onUpdateCitations={() => undefined}
        onRequestCreateWorkspace={() => undefined}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Create workspace" }),
    ).toBeInTheDocument();
  });

  it("shows start conversation when workspace selected and no messages", () => {
    render(
      <ChatPanel
        workspaceId="ws-1"
        conversationId={null}
        onConversationCreated={() => undefined}
        onUpdateCitations={() => undefined}
        onRequestStartConversation={() => undefined}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Start conversation" }),
    ).toBeInTheDocument();
  });
});
