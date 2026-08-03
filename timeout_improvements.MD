Here, you have three realistic options:

1. **Let the stream lifetime define the “timeout” (recommended)**

Since `chatService.streamChat(...)` already calls `done` / `error` callbacks and you call `emitter.complete()` there, the simplest is to remove the hard 3-minute cap and just rely on stream completion:

```java
@PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamMessage(...) {
    workspaces.requireExists(workspaceId);

    // effectively "no Spring timeout"; rely on done/error callbacks
    SseEmitter emitter = new SseEmitter(-1L);

    chatService.streamChat(
        workspaceId,
        id,
        request.query(),
        chunk -> {
            try {
                emitter.send(SseEmitter.event().name("chunk").data(chunk));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        },
        citations -> { ... },
        () -> {
            try {
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        },
        error -> { ... }
    );

    return emitter;
}
```

Then tune idle/keep-alive on Tomcat/NGINX etc. instead of `SseEmitter`.

2. **Dynamic “idle timeout” based on stream activity**

If you still want a cap but want it to move with the stream, start with a base timeout and extend it whenever data flows:

```java
SseEmitter emitter = new SseEmitter(30_000L); // 30s initial

chatService.streamChat(
    workspaceId,
    id,
    request.query(),
    chunk -> {
        try {
            emitter.send(SseEmitter.event().name("chunk").data(chunk));
            emitter.extendTimeOut(30_000L); // add 30s each time a chunk arrives
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    },
    citations -> {
        try {
            emitter.send(SseEmitter.event().name("citations").data(citations));
            emitter.extendTimeOut(30_000L);
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    },
    () -> { ... },
    error -> { ... }
);
```

This effectively makes the timeout “based on the stream return”: if the backend is still streaming chunks, the timeout keeps getting pushed out; if it stalls, the emitter eventually times out.

3. **Client-driven but clamped timeout**

If you want the frontend to influence it:

```java
public record StreamMessageRequest(@NotBlank String query, Long timeoutMs) {}

...

long requested = request.timeoutMs() == null ? 60_000L : request.timeoutMs();
long timeout = Math.min(Math.max(requested, 5_000L), 300_000L); // clamp 5s–5min
SseEmitter emitter = new SseEmitter(timeout);
```

You can combine this with option 2 (extend on chunks) if needed.

---
