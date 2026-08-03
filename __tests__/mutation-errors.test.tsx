import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Sidebar from "@/components/sidebar";
import {
  AtlasApiError,
  createWorkspace,
  deleteDocument,
  fetchConversations,
  fetchDocuments,
  fetchWorkspaces,
  uploadDocument,
} from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
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
  };
});

describe("mutation errors (US2)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchWorkspaces).mockResolvedValue([
      { id: "ws-1", name: "Alpha", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    vi.mocked(fetchDocuments).mockResolvedValue([]);
    vi.mocked(fetchConversations).mockResolvedValue([]);
  });

  it("keeps create workspace modal open and shows error on failure", async () => {
    const user = userEvent.setup();
    vi.mocked(createWorkspace).mockRejectedValue(
      new AtlasApiError("VALIDATION_ERROR", "name must not be blank"),
    );

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={vi.fn()}
        currentConversationId={null}
        onSelectConversation={vi.fn()}
        activeTab="chat"
        onSelectTab={vi.fn()}
        refreshTrigger={0}
        onRefresh={vi.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByText("Alpha")).toBeInTheDocument());

    await user.click(screen.getAllByRole("button", { name: /^New$/ })[0]);
    const input = screen.getByPlaceholderText(/research papers/i);
    await user.clear(input);
    await user.type(input, "Bad");
    await user.click(screen.getByRole("button", { name: /^Confirm$/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        /name must not be blank/i,
      );
    });
    expect(screen.getByPlaceholderText(/research papers/i)).toHaveValue("Bad");
  });

  it("shows delete document error without removing the row", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchDocuments).mockResolvedValue([
      {
        id: "d-1",
        filename: "notes.md",
        contentType: "text/markdown",
        status: "COMPLETE",
        createdAt: "2026-01-01T00:00:00Z",
      },
    ]);
    vi.mocked(deleteDocument).mockRejectedValue(
      new AtlasApiError("PROVIDER_UNAVAILABLE", "Backend down"),
    );

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={vi.fn()}
        currentConversationId={null}
        onSelectConversation={vi.fn()}
        activeTab="documents"
        onSelectTab={vi.fn()}
        refreshTrigger={0}
        onRefresh={vi.fn()}
      />,
    );

    await waitFor(() =>
      expect(screen.getByText("notes.md")).toBeInTheDocument(),
    );

    const deleteBtns = screen
      .getAllByRole("button")
      .filter((b) => b.querySelector("svg"));
    // Click trash on document row — use title if present; otherwise last hover trash
    const docRow =
      screen.getByText("notes.md").closest("div.group") ??
      screen.getByText("notes.md").parentElement?.parentElement;
    const trash = docRow?.querySelector("button");
    expect(trash).toBeTruthy();
    await user.click(trash!);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/Backend down/i);
    });
    expect(screen.getByText("notes.md")).toBeInTheDocument();
  });

  it("shows upload rejection without listing the file", async () => {
    vi.mocked(uploadDocument).mockRejectedValue(
      new AtlasApiError("UPLOAD_UNSUPPORTED_TYPE", "Unsupported file type"),
    );

    render(
      <Sidebar
        currentWorkspaceId="ws-1"
        onSelectWorkspace={vi.fn()}
        currentConversationId={null}
        onSelectConversation={vi.fn()}
        activeTab="documents"
        onSelectTab={vi.fn()}
        refreshTrigger={0}
        onRefresh={vi.fn()}
      />,
    );

    await waitFor(() => expect(fetchDocuments).toHaveBeenCalled());

    const input = document.querySelector(
      "#atlas-doc-upload",
    ) as HTMLInputElement;
    expect(input).toBeTruthy();

    const file = new File(["x"], "notes.pdf", { type: "application/pdf" });
    await userEvent.upload(input, file);

    await waitFor(() => {
      expect(uploadDocument).toHaveBeenCalled();
      expect(screen.getByRole("alert")).toHaveTextContent(/Unsupported/i);
    });
    expect(screen.queryByText("notes.pdf")).not.toBeInTheDocument();
  });
});
