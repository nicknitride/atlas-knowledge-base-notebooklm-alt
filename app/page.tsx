"use client";

import { useEffect, useMemo, useState } from "react";
import Sidebar from "@/components/sidebar";
import ChatPanel from "@/components/chat-panel";
import SourcesPanel from "@/components/sources-panel";
import { Button } from "@/components/ui/button";
import { Citation } from "@/lib/api";
import { computeSourcesVisible } from "@/lib/sources-visibility";
import { PanelRight, PanelLeft } from "lucide-react";

export default function Home() {
  const [currentWorkspaceId, setCurrentWorkspaceId] = useState<string | null>(
    null,
  );
  const [currentConversationId, setCurrentConversationId] = useState<
    string | null
  >(null);
  const [activeTab, setActiveTab] = useState<"chat" | "documents">("chat");
  const [citations, setCitations] = useState<Citation[]>([]);
  const [refreshTrigger, setRefreshTrigger] = useState<number>(0);
  const [navOpen, setNavOpen] = useState(true);
  const [sourcesForcedOpen, setSourcesForcedOpen] = useState(false);
  const [sourcesUserCollapsed, setSourcesUserCollapsed] = useState(false);
  const [requestCreateWorkspace, setRequestCreateWorkspace] = useState(0);
  const [requestStartConversation, setRequestStartConversation] = useState(0);
  const [focusComposeToken, setFocusComposeToken] = useState(0);

  const sourcesVisible = useMemo(
    () =>
      computeSourcesVisible({
        citationCount: citations.length,
        sourcesForcedOpen,
        sourcesUserCollapsed,
      }),
    [citations.length, sourcesForcedOpen, sourcesUserCollapsed],
  );

  useEffect(() => {
    if (citations.length > 0) {
      setSourcesForcedOpen(false);
      if (!sourcesUserCollapsed) {
        // citations available — panel shows via formula
      }
    }
  }, [citations.length, sourcesUserCollapsed]);

  const handleSelectWorkspace = (id: string | null) => {
    setCurrentWorkspaceId(id);
    setCurrentConversationId(null);
    setCitations([]);
    setSourcesForcedOpen(false);
    setSourcesUserCollapsed(false);
  };

  const handleSelectConversation = (id: string | null) => {
    setCurrentConversationId(id);
    setCitations([]);
    setSourcesForcedOpen(false);
    setSourcesUserCollapsed(false);
  };

  const handleRefresh = () => {
    setRefreshTrigger((prev) => prev + 1);
  };

  const toggleSources = () => {
    if (sourcesVisible) {
      if (citations.length > 0) {
        setSourcesUserCollapsed(true);
      }
      setSourcesForcedOpen(false);
    } else {
      setSourcesUserCollapsed(false);
      setSourcesForcedOpen(true);
    }
  };

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {!navOpen ? (
        <div className="flex flex-col border-r border-border bg-sidebar p-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="Show navigation"
            onClick={() => setNavOpen(true)}
          >
            <PanelLeft size={16} />
          </Button>
        </div>
      ) : null}

      {navOpen ? (
        <Sidebar
          currentWorkspaceId={currentWorkspaceId}
          onSelectWorkspace={handleSelectWorkspace}
          currentConversationId={currentConversationId}
          onSelectConversation={handleSelectConversation}
          onConversationCreated={(id) => {
            setCurrentConversationId(id);
            setFocusComposeToken((n) => n + 1);
            handleRefresh();
          }}
          activeTab={activeTab}
          onSelectTab={setActiveTab}
          refreshTrigger={refreshTrigger}
          onRefresh={handleRefresh}
          navOpen={navOpen}
          onNavOpenChange={setNavOpen}
          requestCreateWorkspace={requestCreateWorkspace}
          requestStartConversation={requestStartConversation}
        />
      ) : null}

      <div className="flex-1 flex flex-col min-w-0 relative">
        <div className="absolute top-3 right-3 z-20 flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            aria-label={sourcesVisible ? "Hide sources" : "Show sources"}
            onClick={toggleSources}
          >
            <PanelRight size={16} />
          </Button>
        </div>
        <ChatPanel
          workspaceId={currentWorkspaceId}
          conversationId={currentConversationId}
          focusComposeToken={focusComposeToken}
          onConversationCreated={(id) => {
            setCurrentConversationId(id);
            setFocusComposeToken((n) => n + 1);
            handleRefresh();
          }}
          onUpdateCitations={setCitations}
          onRequestCreateWorkspace={() =>
            setRequestCreateWorkspace((n) => n + 1)
          }
          onRequestStartConversation={() =>
            setRequestStartConversation((n) => n + 1)
          }
        />
      </div>

      {sourcesVisible ? (
        <SourcesPanel citations={citations} onClose={toggleSources} />
      ) : null}
    </div>
  );
}
