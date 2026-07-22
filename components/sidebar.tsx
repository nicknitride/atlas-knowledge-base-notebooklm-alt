'use client'

import { useState } from 'react'
import { Menu, X, Plus, Settings, Search, Home } from 'lucide-react'
import { Button } from './ui/button'

export default function Sidebar() {
  const [isOpen, setIsOpen] = useState(true)

  return (
    <>
      {/* Mobile toggle button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="fixed top-4 left-4 z-50 md:hidden bg-card border border-border rounded-lg p-2"
        aria-label="Toggle sidebar"
      >
        {isOpen ? <X size={20} /> : <Menu size={20} />}
      </button>

      {/* Sidebar overlay for mobile */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 md:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed md:relative top-0 left-0 h-screen w-64 bg-sidebar border-r border-sidebar-border flex flex-col transition-transform duration-300 z-40 md:z-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        }`}
      >
        {/* Header */}
        <div className="p-6 border-b border-sidebar-border">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
              <span className="text-primary-foreground font-bold text-lg">A</span>
            </div>
            <span className="font-bold text-lg">Atlas</span>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto p-4">
          <div className="space-y-2">
            {/* Workspace Selector */}
            <div className="mb-6">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-2 block mb-2">
                Workspace
              </label>
              <button className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-sidebar-accent/50 bg-sidebar-accent text-left text-sm font-medium transition-colors">
                <div className="w-3 h-3 bg-primary rounded" />
                <span>Default Workspace</span>
              </button>
            </div>

            {/* Main Navigation */}
            <div className="mb-6">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-2 block mb-2">
                Menu
              </label>
              <div className="space-y-1">
                <button className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-sidebar-accent/50 text-left text-sm font-medium transition-colors">
                  <Home size={16} />
                  <span>Conversations</span>
                </button>
                <button className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-sidebar-accent/50 text-left text-sm font-medium transition-colors">
                  <Search size={16} />
                  <span>Documents</span>
                </button>
              </div>
            </div>

            {/* Recent Conversations */}
            <div className="mb-6">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider px-2 block mb-2">
                Recent
              </label>
              <div className="space-y-1">
                {['Quarterly Summary', 'Budget Analysis', 'Team Meeting Notes'].map((item) => (
                  <button
                    key={item}
                    className="w-full px-3 py-2 rounded-lg hover:bg-sidebar-accent/50 text-left text-sm text-sidebar-foreground/70 hover:text-sidebar-foreground transition-colors truncate"
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </nav>

        {/* Footer Actions */}
        <div className="p-4 border-t border-sidebar-border space-y-2">
          <Button className="w-full" size="sm" variant="outline">
            <Plus size={16} />
            New Conversation
          </Button>
          <button className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg hover:bg-sidebar-accent/50 text-sm font-medium transition-colors text-sidebar-foreground/70 hover:text-sidebar-foreground">
            <Settings size={16} />
            <span>Settings</span>
          </button>
        </div>
      </aside>
    </>
  )
}
