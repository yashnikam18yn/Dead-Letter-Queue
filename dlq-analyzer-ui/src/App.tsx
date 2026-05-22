import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useWebSocket } from './hooks/useWebSocket'
import { useDlqContext, DlqProvider } from './context/DlqContext'
import type { WebSocketMessage } from './types'
import Dashboard from './pages/Dashboard'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    }
  }
})

function AppInner() {
  const { setLiveCount } = useDlqContext()

  const handleWebSocketMessage = (msg: WebSocketMessage) => {
    if (msg.event === 'NEW_MESSAGES') {
      setLiveCount(msg.totalPending ?? 0)
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.invalidateQueries({ queryKey: ['messages'] })
    }
    if (msg.event === 'REPLAY_COMPLETE') {
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.invalidateQueries({ queryKey: ['messages'] })
      queryClient.invalidateQueries({ queryKey: ['audit'] })
    }
  }

  useWebSocket({ onMessage: handleWebSocketMessage })

  return (
    <div style={{ fontFamily: 'Arial, sans-serif', minHeight: '100vh', backgroundColor: '#f5f5f5' }}>

      {/* Top bar */}
      <div style={{
        backgroundColor: '#ff6600',
        color: '#fff',
        padding: '10px 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <strong style={{ fontSize: '18px' }}>DLQ Analyzer</strong>
        <span style={{ fontSize: '13px' }}>● Live</span>
      </div>

      <Dashboard />
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <DlqProvider>
        <AppInner />
      </DlqProvider>
    </QueryClientProvider>
  )
}