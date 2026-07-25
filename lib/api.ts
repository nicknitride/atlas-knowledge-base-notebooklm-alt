const API_BASE =
  process.env.NEXT_PUBLIC_ATLAS_API_URL || "http://localhost:8080";

export interface Workspace {
  id: string;
  name: string;
  createdAt: string;
}

export interface DocumentItem {
  id: string;
  filename: string;
  contentType: string;
  status: "PENDING" | "PROCESSING" | "COMPLETE" | "FAILED";
  failureReason?: string;
  createdAt: string;
}

export interface Conversation {
  id: string;
  workspaceId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Citation {
  chunkId: string;
  documentId: string;
  documentFilename: string;
  ordinal: number;
  sourceLocator: string;
  snippet: string;
  similarity: number;
}

export interface Message {
  id: string;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  createdAt: string;
  citations?: Citation[];
}

export interface ConversationDetail extends Conversation {
  messages: Message[];
}

export async function fetchWorkspaces(): Promise<Workspace[]> {
  const res = await fetch(`${API_BASE}/api/workspaces`);
  if (!res.ok) throw new Error("Failed to fetch workspaces");
  return res.json();
}

export async function createWorkspace(name: string): Promise<Workspace> {
  const res = await fetch(`${API_BASE}/api/workspaces`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (!res.ok) throw new Error("Failed to create workspace");
  return res.json();
}

export async function renameWorkspace(
  id: string,
  name: string,
): Promise<Workspace> {
  const res = await fetch(`${API_BASE}/api/workspaces/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (!res.ok) throw new Error("Failed to rename workspace");
  return res.json();
}

export async function deleteWorkspace(id: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/workspaces/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error("Failed to delete workspace");
}

export async function fetchDocuments(
  workspaceId: string,
): Promise<DocumentItem[]> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/documents`,
  );
  if (!res.ok) throw new Error("Failed to fetch documents");
  return res.json();
}

export async function uploadDocument(
  workspaceId: string,
  file: File,
): Promise<DocumentItem> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/documents`,
    {
      method: "POST",
      body: formData,
    },
  );
  if (!res.ok) {
    const err = await res.json().catch(() => null);
    throw new Error(err?.message || "Failed to upload document");
  }
  return res.json();
}

export async function deleteDocument(
  workspaceId: string,
  documentId: string,
): Promise<void> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/documents/${documentId}`,
    {
      method: "DELETE",
    },
  );
  if (!res.ok) throw new Error("Failed to delete document");
}

export async function fetchConversations(
  workspaceId: string,
): Promise<Conversation[]> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations`,
  );
  if (!res.ok) throw new Error("Failed to fetch conversations");
  return res.json();
}

export async function createConversation(
  workspaceId: string,
  title?: string,
): Promise<Conversation> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: title || "New conversation" }),
    },
  );
  if (!res.ok) throw new Error("Failed to create conversation");
  return res.json();
}

export async function fetchConversationDetail(
  workspaceId: string,
  conversationId: string,
): Promise<ConversationDetail> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}`,
  );
  if (!res.ok) throw new Error("Failed to fetch conversation detail");
  return res.json();
}

export async function renameConversation(
  workspaceId: string,
  conversationId: string,
  title: string,
): Promise<Conversation> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title }),
    },
  );
  if (!res.ok) throw new Error("Failed to rename conversation");
  return res.json();
}

export async function deleteConversation(
  workspaceId: string,
  conversationId: string,
): Promise<void> {
  const res = await fetch(
    `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}`,
    {
      method: "DELETE",
    },
  );
  if (!res.ok) throw new Error("Failed to delete conversation");
}

export function streamChatMessage(
  workspaceId: string,
  conversationId: string,
  query: string,
  onChunk: (chunk: string) => void,
  onCitations: (citations: Citation[]) => void,
  onComplete: () => void,
  onError: (err: Error) => void,
): () => void {
  const url = `${API_BASE}/api/workspaces/${workspaceId}/conversations/${conversationId}/messages/stream`;
  const controller = new AbortController();

  fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ query }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        throw new Error(`Failed to initiate stream (${response.status})`);
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      let currentEvent = "message";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          if (line.startsWith("event:")) {
            currentEvent = line.substring(6).trim();
          } else if (line.startsWith("data:")) {
            const data = line.startsWith("data: ")
              ? line.substring(6)
              : line.substring(5);
            if (currentEvent === "chunk") {
              onChunk(data);
            } else if (currentEvent === "citations") {
              try {
                onCitations(JSON.parse(data.trim()));
              } catch (err) {
                console.error("Failed to parse citations", err);
              }
            } else if (currentEvent === "done") {
              onComplete();
              return;
            }
          }
        }
      }
      onComplete();
    })
    .catch((err) => {
      if (err.name !== "AbortError") {
        onError(err);
      }
    });

  return () => {
    controller.abort();
  };
}
