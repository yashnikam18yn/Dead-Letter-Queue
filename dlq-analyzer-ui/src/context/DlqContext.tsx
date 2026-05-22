import { createContext, useContext, useState, type ReactNode } from 'react'
import type { ErrorGroup, DlqMessage } from '../types'

interface DlqContextType {
  selectedGroup: ErrorGroup | null
  setSelectedGroup: (group: ErrorGroup | null) => void
  selectedMessage: DlqMessage | null
  setSelectedMessage: (message: DlqMessage | null) => void
  messages: DlqMessage[]
  setMessages: (messages: DlqMessage[]) => void
  liveCount: number
  setLiveCount: (count: number) => void
}

const DlqContext = createContext<DlqContextType | null>(null)

export const DlqProvider = ({ children }: { children: ReactNode }) => {
  const [selectedGroup, setSelectedGroup] = useState<ErrorGroup | null>(null)
  const [selectedMessage, setSelectedMessage] = useState<DlqMessage | null>(null)
  const [messages, setMessages] = useState<DlqMessage[]>([])
  const [liveCount, setLiveCount] = useState(0)

  return (
    <DlqContext.Provider value={{
      selectedGroup, setSelectedGroup,
      selectedMessage, setSelectedMessage,
      messages, setMessages,
      liveCount, setLiveCount
    }}>
      {children}
    </DlqContext.Provider>
  )
}

export const useDlqContext = () => {
  const ctx = useContext(DlqContext)
  if (!ctx) throw new Error('useDlqContext must be used inside DlqProvider')
  return ctx
}