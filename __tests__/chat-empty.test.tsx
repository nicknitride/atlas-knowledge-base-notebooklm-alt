import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ChatPanel from "@/components/chat-panel";
import { fetchConversationDetail } from "@/lib/api";

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

  it("shows start conversation and suggestions when workspace selected and no conversation (pre-start)", () => {
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
    expect(
      screen.getByText(/Summarize workspace key points/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/What are the primary findings/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Compare document highlights/i),
    ).toBeInTheDocument();
  });

  it("empty-thread: no Start CTA or suggestions when conversation selected with zero messages", async () => {
    vi.mocked(fetchConversationDetail).mockResolvedValue({
      id: "c-1",
      title: "Empty",
      messages: [],
    } as never);

    render(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-1"
        onConversationCreated={() => undefined}
        onUpdateCitations={() => undefined}
      />,
    );

    await waitFor(() => {
      expect(fetchConversationDetail).toHaveBeenCalled();
    });

    expect(
      screen.queryByRole("button", { name: "Start conversation" }),
    ).toBeNull();
    expect(screen.queryByText(/Summarize workspace key points/i)).toBeNull();
    expect(
      screen.getByRole("textbox", { name: "Message compose" }),
    ).not.toBeDisabled();
  });
});
