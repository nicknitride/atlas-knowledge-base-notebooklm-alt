import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { streamChatMessage, AtlasApiError } from "@/lib/api";

function sseBody(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let i = 0;
  return new ReadableStream({
    pull(controller) {
      if (i >= chunks.length) {
        controller.close();
        return;
      }
      controller.enqueue(encoder.encode(chunks[i++]));
    },
  });
}

describe("streamChatMessage SSE error events (US3)", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("calls onError and not onComplete for event: error", async () => {
    const onChunk = vi.fn();
    const onCitations = vi.fn();
    const onComplete = vi.fn();
    const onError = vi.fn();

    vi.mocked(fetch).mockResolvedValue(
      new Response(
        sseBody([
          'event: error\ndata: {"code":"PROVIDER_UNAVAILABLE","message":"Ollama down"}\n\n',
        ]),
        {
          status: 200,
          headers: { "Content-Type": "text/event-stream" },
        },
      ),
    );

    streamChatMessage(
      "ws-1",
      "c-1",
      "hello",
      onChunk,
      onCitations,
      onComplete,
      onError,
    );

    await vi.waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onComplete).not.toHaveBeenCalled();
    expect(onError.mock.calls[0][0]).toMatchObject({
      code: "PROVIDER_UNAVAILABLE",
      message: "Ollama down",
    });
  });

  it("treats stream close without done as error", async () => {
    const onComplete = vi.fn();
    const onError = vi.fn();

    vi.mocked(fetch).mockResolvedValue(
      new Response(sseBody(['event: chunk\ndata: hi\n\n']), {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      }),
    );

    streamChatMessage(
      "ws-1",
      "c-1",
      "hello",
      vi.fn(),
      vi.fn(),
      onComplete,
      onError,
    );

    await vi.waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onComplete).not.toHaveBeenCalled();
  });

  it("calls onComplete on done event", async () => {
    const onComplete = vi.fn();
    const onError = vi.fn();

    vi.mocked(fetch).mockResolvedValue(
      new Response(
        sseBody([
          "event: chunk\ndata: hi\n\n",
          "event: done\ndata: [DONE]\n\n",
        ]),
        {
          status: 200,
          headers: { "Content-Type": "text/event-stream" },
        },
      ),
    );

    streamChatMessage(
      "ws-1",
      "c-1",
      "hello",
      vi.fn(),
      vi.fn(),
      onComplete,
      onError,
    );

    await vi.waitFor(() => expect(onComplete).toHaveBeenCalled());
    expect(onError).not.toHaveBeenCalled();
  });

  it("abort does not call onError", async () => {
    const onError = vi.fn();
    const onComplete = vi.fn();

    let rejectFetch: (err: Error) => void = () => undefined;
    vi.mocked(fetch).mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectFetch = reject;
        }),
    );

    const cancel = streamChatMessage(
      "ws-1",
      "c-1",
      "hello",
      vi.fn(),
      vi.fn(),
      onComplete,
      onError,
    );

    const abortErr = new Error("Aborted");
    abortErr.name = "AbortError";
    cancel();
    rejectFetch(abortErr);

    await new Promise((r) => setTimeout(r, 20));
    expect(onError).not.toHaveBeenCalled();
    expect(onComplete).not.toHaveBeenCalled();
  });
});
