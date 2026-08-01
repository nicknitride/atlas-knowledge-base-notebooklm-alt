import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ChatPanel from "@/components/chat-panel";
import {
  AtlasApiError,
  fetchConversationDetail,
  streamChatMessage,
} from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    fetchConversationDetail: vi.fn(),
    createConversation: vi.fn(),
    streamChatMessage: vi.fn(),
  };
});

describe("chat stream errors UI (US3)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    vi.mocked(fetchConversationDetail).mockResolvedValue({
      id: "c-1",
      workspaceId: "ws-1",
      title: "Chat",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      messages: [],
    });
  });

  it("shows mapped error and clears loading on stream onError", async () => {
    const user = userEvent.setup();
    vi.mocked(streamChatMessage).mockImplementation(
      (_ws, _c, _q, _chunk, _cit, _done, onError) => {
        queueMicrotask(() => {
          onError(
            new AtlasApiError("PROVIDER_UNAVAILABLE", "Ollama is down"),
          );
        });
        return () => undefined;
      },
    );

    render(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-1"
        onConversationCreated={vi.fn()}
        onUpdateCitations={vi.fn()}
      />,
    );

    await waitFor(() => expect(fetchConversationDetail).toHaveBeenCalled());

    const input = screen.getByPlaceholderText(/Ask a question/i);
    await user.type(input, "What is Atlas?");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/Ollama is down/i);
    });
  });

  it("aborts stream when conversation changes", async () => {
    const cancel = vi.fn();
    vi.mocked(streamChatMessage).mockReturnValue(cancel);

    const { rerender } = render(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-1"
        onConversationCreated={vi.fn()}
        onUpdateCitations={vi.fn()}
      />,
    );

    await waitFor(() => expect(fetchConversationDetail).toHaveBeenCalled());

    vi.mocked(streamChatMessage).mockImplementation(() => cancel);
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText(/Ask a question/i), "Hi");
    await user.keyboard("{Enter}");

    await waitFor(() => expect(streamChatMessage).toHaveBeenCalled());

    rerender(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-2"
        onConversationCreated={vi.fn()}
        onUpdateCitations={vi.fn()}
      />,
    );

    await waitFor(() => expect(cancel).toHaveBeenCalled());
  });
});
