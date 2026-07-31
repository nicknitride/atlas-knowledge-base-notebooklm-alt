import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ChatPanel from "@/components/chat-panel";

vi.mock("@/lib/api", () => ({
  fetchConversationDetail: vi.fn(),
  createConversation: vi.fn(),
  streamChatMessage: vi.fn(),
}));

describe("documents nav keeps chat main surface", () => {
  it("chat main region remains mounted regardless of documents-oriented nav", () => {
    render(
      <div data-testid="shell">
        <div data-testid="nav-documents-active" aria-current="page">
          Documents
        </div>
        <ChatPanel
          workspaceId="ws-1"
          conversationId={null}
          onConversationCreated={() => undefined}
          onUpdateCitations={() => undefined}
        />
      </div>,
    );
    expect(screen.getByTestId("chat-main")).toBeInTheDocument();
    expect(screen.getByTestId("nav-documents-active")).toBeInTheDocument();
  });
});
