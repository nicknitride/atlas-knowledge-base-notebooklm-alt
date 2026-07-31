import { describe, expect, it } from "vitest";
import { deriveChatMainMode } from "@/lib/chat-main-mode";

describe("deriveChatMainMode", () => {
  it("returns no-workspace when workspaceId is null", () => {
    expect(
      deriveChatMainMode({
        workspaceId: null,
        conversationId: null,
        messageCount: 0,
      }),
    ).toBe("no-workspace");
  });

  it("returns pre-start when workspace selected and no conversation", () => {
    expect(
      deriveChatMainMode({
        workspaceId: "ws-1",
        conversationId: null,
        messageCount: 0,
      }),
    ).toBe("pre-start");
  });

  it("returns empty-thread when conversation selected with zero messages", () => {
    expect(
      deriveChatMainMode({
        workspaceId: "ws-1",
        conversationId: "c-1",
        messageCount: 0,
      }),
    ).toBe("empty-thread");
  });

  it("returns active-thread when conversation has messages", () => {
    expect(
      deriveChatMainMode({
        workspaceId: "ws-1",
        conversationId: "c-1",
        messageCount: 3,
      }),
    ).toBe("active-thread");
  });

  it("treats pre-start and empty-thread as mutually exclusive at messageCount 0", () => {
    const preStart = deriveChatMainMode({
      workspaceId: "ws-1",
      conversationId: null,
      messageCount: 0,
    });
    const emptyThread = deriveChatMainMode({
      workspaceId: "ws-1",
      conversationId: "c-1",
      messageCount: 0,
    });
    expect(preStart).toBe("pre-start");
    expect(emptyThread).toBe("empty-thread");
    expect(preStart).not.toBe(emptyThread);
  });
});
