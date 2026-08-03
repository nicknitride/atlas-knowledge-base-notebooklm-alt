export type ChatMainMode =
  "no-workspace" | "pre-start" | "empty-thread" | "active-thread";

export function deriveChatMainMode(input: {
  workspaceId: string | null;
  conversationId: string | null;
  messageCount: number;
}): ChatMainMode {
  if (!input.workspaceId) return "no-workspace";
  if (!input.conversationId) return "pre-start";
  if (input.messageCount === 0) return "empty-thread";
  return "active-thread";
}
