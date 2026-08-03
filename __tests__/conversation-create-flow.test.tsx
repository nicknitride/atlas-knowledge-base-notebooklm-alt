import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ChatPanel from "@/components/chat-panel";
import Sidebar from "@/components/sidebar";
import {
  createConversation,
  fetchConversationDetail,
  fetchConversations,
  fetchDocuments,
  fetchWorkspaces,
} from "@/lib/api";

vi.mock("@/lib/api", () => ({
  fetchWorkspaces: vi.fn(),
  createWorkspace: vi.fn(),
  deleteWorkspace: vi.fn(),
  renameWorkspace: vi.fn(),
  fetchDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  deleteDocument: vi.fn(),
  fetchConversations: vi.fn(),
  createConversation: vi.fn(),
  deleteConversation: vi.fn(),
  renameConversation: vi.fn(),
  fetchConversationDetail: vi.fn(),
  streamChatMessage: vi.fn(),
}));

describe("conversation create flow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-1", name: "Workspace One" } as never,
    ]);
    vi.mocked(fetchConversations).mockResolvedValue([]);
    vi.mocked(fetchDocuments).mockResolvedValue([]);
    vi.mocked(fetchConversationDetail).mockResolvedValue({
      id: "c-new",
      title: "My chat",
      messages: [],
    } as never);
  });

  it("empty-thread: no Start CTA or suggestions when conversation selected with zero messages", async () => {
    render(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-new"
        focusComposeToken={0}
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
      screen.getByPlaceholderText(/Ask a question grounded/i),
    ).not.toBeDisabled();
  });

  it("successful create selects conversation, switches to chat, focuses compose", async () => {
    const user = userEvent.setup();
    const onSelectConversation = vi.fn();
    const onSelectTab = vi.fn();
    const onConversationCreated = vi.fn();

    vi.mocked(createConversation).mockResolvedValue({
      id: "c-new",
      title: "My chat",
    } as never);

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={() => undefined}
        currentConversationId={null}
        onSelectConversation={onSelectConversation}
        onConversationCreated={onConversationCreated}
        activeTab="chat"
        onSelectTab={onSelectTab}
        refreshTrigger={0}
        onRefresh={() => undefined}
      />,
    );

    await waitFor(() => {
      expect(fetchConversations).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("button", { name: /New Conversation/i }));
    await user.type(
      screen.getByPlaceholderText(/Rust Documentation/i),
      "My chat",
    );
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() => {
      expect(createConversation).toHaveBeenCalled();
    });
    expect(onSelectConversation).toHaveBeenCalledWith("c-new");
    expect(onSelectTab).toHaveBeenCalledWith("chat");
    expect(onConversationCreated).toHaveBeenCalledWith("c-new");
  });

  it("create while Documents tab switches to Chat and selects conversation", async () => {
    const user = userEvent.setup();
    const onSelectConversation = vi.fn();
    const onSelectTab = vi.fn();
    const onConversationCreated = vi.fn();

    vi.mocked(createConversation).mockResolvedValue({
      id: "c-docs",
      title: "From docs",
    } as never);

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={() => undefined}
        currentConversationId={null}
        onSelectConversation={onSelectConversation}
        onConversationCreated={onConversationCreated}
        activeTab="documents"
        onSelectTab={onSelectTab}
        refreshTrigger={0}
        onRefresh={() => undefined}
      />,
    );

    await waitFor(() => {
      expect(fetchDocuments).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("button", { name: /New Conversation/i }));
    await user.type(
      screen.getByPlaceholderText(/Rust Documentation/i),
      "From docs",
    );
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() => {
      expect(onSelectTab).toHaveBeenCalledWith("chat");
      expect(onSelectConversation).toHaveBeenCalledWith("c-docs");
      expect(onConversationCreated).toHaveBeenCalledWith("c-docs");
    });
  });

  it("cancel and blank name leave selection unchanged; API failure does not select", async () => {
    const user = userEvent.setup();
    const onSelectConversation = vi.fn();
    const onConversationCreated = vi.fn();

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={() => undefined}
        currentConversationId={null}
        onSelectConversation={onSelectConversation}
        onConversationCreated={onConversationCreated}
        activeTab="chat"
        onSelectTab={() => undefined}
        refreshTrigger={0}
        onRefresh={() => undefined}
      />,
    );

    await waitFor(() => {
      expect(fetchConversations).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("button", { name: /New Conversation/i }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onSelectConversation).not.toHaveBeenCalled();
    expect(onConversationCreated).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /New Conversation/i }));
    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    vi.mocked(createConversation).mockRejectedValueOnce(new Error("fail"));
    await user.click(screen.getByRole("button", { name: /New Conversation/i }));
    await user.type(
      screen.getByPlaceholderText(/Rust Documentation/i),
      "Will fail",
    );
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() => {
      expect(createConversation).toHaveBeenCalled();
    });
    expect(onSelectConversation).not.toHaveBeenCalled();
    expect(onConversationCreated).not.toHaveBeenCalled();
    expect(screen.getByText(/fail/i)).toBeInTheDocument();
  });

  it("focuses compose when focusComposeToken bumps; list select without token does not", async () => {
    const { rerender } = render(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-existing"
        focusComposeToken={0}
        onConversationCreated={() => undefined}
        onUpdateCitations={() => undefined}
      />,
    );

    await waitFor(() => {
      expect(fetchConversationDetail).toHaveBeenCalled();
    });

    const compose = screen.getByRole("textbox", { name: "Message compose" });
    expect(compose).not.toHaveFocus();

    rerender(
      <ChatPanel
        workspaceId="ws-1"
        conversationId="c-new"
        focusComposeToken={1}
        onConversationCreated={() => undefined}
        onUpdateCitations={() => undefined}
      />,
    );

    await waitFor(() => {
      expect(
        screen.getByRole("textbox", { name: "Message compose" }),
      ).toHaveFocus();
    });
  });
});
