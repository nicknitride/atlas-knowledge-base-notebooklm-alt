"use client";

import { useState, useEffect, useRef } from "react";
import { Streamdown } from "streamdown";
import remarkGfm from "remark-gfm";
import {
  Plus,
  Sparkles,
  ArrowUp,
  Square,
  RefreshCw,
  FileText,
  AlertCircle,
} from "lucide-react";
import { Button } from "./ui/button";
import {
  Message,
  Citation,
  fetchConversationDetail,
  createConversation,
  streamChatMessage,
} from "@/lib/api";

interface ChatPanelProps {
  workspaceId: string | null;
  conversationId: string | null;
  onConversationCreated: (id: string) => void;
  onUpdateCitations: (citations: Citation[]) => void;
}

export default function ChatPanel({
  workspaceId,
  conversationId,
  onConversationCreated,
  onUpdateCitations,
}: ChatPanelProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cancelStreamRef = useRef<(() => void) | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Scroll to bottom when messages update
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  // Fetch Conversation detail when conversationId changes
  useEffect(() => {
    if (workspaceId && conversationId) {
      loadConversation(workspaceId, conversationId);
    } else {
      setMessages([]);
      onUpdateCitations([]);
    }
  }, [workspaceId, conversationId]);

  const loadConversation = async (wsId: string, convId: string) => {
    try {
      setError(null);
      const detail = await fetchConversationDetail(wsId, convId);
      setMessages(detail.messages);
      // Extract latest assistant message citations if available
      const lastAssistantMsg = [...detail.messages]
        .reverse()
        .find((m) => m.role === "ASSISTANT");
      if (lastAssistantMsg?.citations) {
        onUpdateCitations(lastAssistantMsg.citations);
      }
    } catch (err) {
      console.error("Failed to load conversation details", err);
      setError("Could not load conversation history.");
    }
  };

  const handleSend = async () => {
    if (!input.trim() || !workspaceId || isLoading) return;
    const userQuery = input.trim();
    setInput("");
    setError(null);

    let activeConvId = conversationId;
    if (!activeConvId) {
      try {
        const newConv = await createConversation(
          workspaceId,
          userQuery.substring(0, 30),
        );
        activeConvId = newConv.id;
        onConversationCreated(newConv.id);
      } catch (err) {
        setError("Failed to create conversation");
        return;
      }
    }

    const tempUserMsg: Message = {
      id: "temp-user-" + Date.now(),
      role: "USER",
      content: userQuery,
      createdAt: new Date().toISOString(),
    };

    const tempAssistantMsg: Message = {//for future token streaming support
      id: "temp-assistant-" + Date.now(),
      role: "ASSISTANT",
      content: "",
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, tempUserMsg]);
    setIsLoading(true);

    let accumulatedContent = "";
    const cancelFn = streamChatMessage(
      workspaceId,
      activeConvId,
      userQuery,
      (chunk) => {
        //Useful for future token streaming support
      },
      (citations) => {
        onUpdateCitations(citations);
      },
      async () => {
          setIsLoading(false);
          cancelStreamRef.current = null;
          await loadConversation(workspaceId, activeConvId);
          },
      (err) => {
        console.error("Stream error", err);
        setError("An error occurred while streaming the answer. Please retry.");
        setIsLoading(false);
        cancelStreamRef.current = null;
      },
    );

    cancelStreamRef.current = cancelFn;
  };

  const handleStopStream = () => {
    if (cancelStreamRef.current) {
      cancelStreamRef.current();
      cancelStreamRef.current = null;
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-screen bg-background relative overflow-hidden">
      {/* Top Header */}
      <div className="border-b border-border px-6 py-4 flex items-center justify-between bg-card/40 backdrop-blur">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center">
            <Sparkles size={20} className="text-primary" />
          </div>
          <div>
            <h1 className="font-semibold text-foreground tracking-tight text-sm md:text-base">
              {conversationId
                ? "Workspace Grounded Assistant"
                : "New Knowledge Query"}
            </h1>
            <p className="text-xs text-muted-foreground">
              Traceable answers grounded in your private workspace documents
            </p>
          </div>
        </div>
      </div>

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto px-4 md:px-8 py-8 space-y-6">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center max-w-lg mx-auto">
            <div className="w-16 h-16 bg-primary/10 rounded-2xl flex items-center justify-center mb-5 shadow-sm">
              <Sparkles size={32} className="text-primary" />
            </div>
            <h2 className="text-xl font-bold text-foreground mb-2">
              Ask Grounded Workspace Questions
            </h2>
            <p className="text-sm text-muted-foreground mb-8 leading-relaxed">
              Upload PDF, Markdown, or Plain-text documents to your workspace.
              Atlas will retrieve relevant chunks and generate accurate, cited
              answers.
            </p>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 w-full">
              {[
                "Summarize workspace key points",
                "What are the primary findings?",
                "Compare document highlights",
              ].map((suggestion) => (
                <button
                  key={suggestion}
                  onClick={() => {
                    setInput(suggestion);
                  }}
                  className="p-3 text-left rounded-xl bg-card border border-border hover:border-primary/50 hover:bg-primary/5 transition-all text-xs font-medium text-foreground/80 hover:text-foreground shadow-sm"
                >
                  "{suggestion}"
                </button>
              ))}
            </div>
          </div>
        ) : (
          <>
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex gap-4 ${message.role === "USER" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-xl lg:max-w-2xl px-5 py-4 rounded-2xl text-sm leading-relaxed shadow-sm ${
                    message.role === "USER"
                      ? "bg-primary text-primary-foreground rounded-br-xs"
                      : "bg-card border border-border text-foreground rounded-bl-xs"
                  }`}
                >
                  <div className="font-semibold text-xs mb-1.5 opacity-80">
                    {message.role === "USER" ? "You" : "Atlas Assistant"}
                  </div>
                  <div className="prose prose-sm dark:prose-invert max-w-none leading-relaxed">
                    <Streamdown
                    key={message.id + "-" + message.content}
                      parseIncompleteMarkdown
                      remarkPlugins={[remarkGfm]}
                    >
                      {message.content}
                    </Streamdown>
                  </div>

                  {/* Render Citation Badges for Assistant */}
                  {message.role === "ASSISTANT" &&
                    message.citations &&
                    message.citations.length > 0 && (
                      <div className="mt-4 pt-3 border-t border-border/40 space-y-1.5">
                        <p className="text-[11px] font-semibold text-muted-foreground flex items-center gap-1">
                          <FileText size={12} /> Sources Cited:
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {message.citations.map((cit, idx) => (
                            <span
                              key={cit.chunkId || idx}
                              className="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 bg-primary/10 text-primary border border-primary/20 rounded-md font-medium"
                              title={cit.snippet}
                            >
                              [{idx + 1}] {cit.documentFilename}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                </div>
              </div>
            ))}
            {isLoading && (
              <div className="flex gap-4 justify-start">
                <div className="bg-card border border-border rounded-2xl rounded-bl-xs px-5 py-4 shadow-sm flex items-center gap-2">
                  <RefreshCw size={16} className="text-primary animate-spin" />
                  <span className="text-xs text-muted-foreground">
                    Synthesizing grounded response...
                  </span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </>
        )}
      </div>

      {/* Error Notification */}
      {error && (
        <div className="mx-6 mb-2 p-3 bg-destructive/10 border border-destructive/20 rounded-xl text-xs text-destructive flex items-center justify-between">
          <span className="flex items-center gap-2">
            <AlertCircle size={14} /> {error}
          </span>
          <button
            onClick={() => setError(null)}
            className="font-medium hover:underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Input Form Footer */}
      <div className="border-t border-border px-4 md:px-6 py-4 bg-card/60 backdrop-blur">
        <div className="max-w-4xl mx-auto flex gap-3">
          <div className="flex-1 relative">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask a question grounded in this workspace... (Enter to send, Shift+Enter for new line)"
              rows={2}
              className="w-full px-4 py-3 rounded-xl bg-input border border-border text-foreground text-sm placeholder-muted-foreground resize-none focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary shadow-inner"
              disabled={isLoading || !workspaceId}
            />
          </div>
          {isLoading ? (
            <Button
              onClick={handleStopStream}
              variant="destructive"
              size="lg"
              className="self-end rounded-xl shadow"
            >
              <Square size={16} />
            </Button>
          ) : (
            <Button
              onClick={handleSend}
              disabled={!input.trim() || !workspaceId}
              size="lg"
              className="self-end rounded-xl shadow"
            >
              <ArrowUp size={18} />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
