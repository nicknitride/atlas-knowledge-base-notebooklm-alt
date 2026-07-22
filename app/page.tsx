import Sidebar from '@/components/sidebar'
import ChatPanel from '@/components/chat-panel'
import SourcesPanel from '@/components/sources-panel'

export default function Home() {
  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <Sidebar />

      {/* Chat Panel - Main Content */}
      <div className="flex-1 flex flex-col md:ml-0">
        <ChatPanel />
      </div>

      {/* Sources Panel */}
      <SourcesPanel />
    </div>
  )
}
