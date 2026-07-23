'use client'

import { useState } from 'react'
import Sidebar from '@/components/sidebar'
import ChatPanel from '@/components/chat-panel'
import SourcesPanel from '@/components/sources-panel'
import { Citation } from '@/lib/api'

export default function Home() {
  const [currentWorkspaceId, setCurrentWorkspaceId] = useState<string | null>(null)
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'chat' | 'documents'>('chat')
  const [citations, setCitations] = useState<Citation[]>([])
  const [refreshTrigger, setRefreshTrigger] = useState<number>(0)

  const handleSelectWorkspace = (id: string) => {
    setCurrentWorkspaceId(id)
    setCurrentConversationId(null)
    setCitations([])
  }

  const handleSelectConversation = (id: string | null) => {
    setCurrentConversationId(id)
    setCitations([])
  }

  const handleRefresh = () => {
    setRefreshTrigger((prev) => prev + 1)
  }

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {/* Sidebar */}
      <Sidebar
        currentWorkspaceId={currentWorkspaceId}
        onSelectWorkspace={handleSelectWorkspace}
        currentConversationId={currentConversationId}
        onSelectConversation={handleSelectConversation}
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        refreshTrigger={refreshTrigger}
        onRefresh={handleRefresh}
      />

      {/* Main Chat Content */}
      <div className="flex-1 flex flex-col min-w-0">
        <ChatPanel
          workspaceId={currentWorkspaceId}
          conversationId={currentConversationId}
          onConversationCreated={(id) => {
            setCurrentConversationId(id)
            handleRefresh()
          }}
          onUpdateCitations={setCitations}
        />
      </div>

      {/* Sources / Citations Panel */}
      <SourcesPanel citations={citations} />
    </div>
  )
}
