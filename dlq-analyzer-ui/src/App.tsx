import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useWebSocket } from './hooks/useWebSocket'
import { useDlqContext, DlqProvider } from './context/DlqContext'
import type { WebSocketMessage } from './types'
import Dashboard from './pages/Dashboard'
import Login from './components/Login'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

function AppInner() {
  const { liveCount, setLiveCount } = useDlqContext()

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
    <div style={styles.app}>
      {/* Top bar */}
      <header style={styles.topbar}>
        <div style={styles.brandWrap}>
          <span style={styles.brand}>DLQ Analyzer</span>
          <span style={styles.tagline}>Dead-letter queue monitor</span>
        </div>

        <div style={styles.meta}>
          <span style={styles.live}>
            <span style={styles.dot} />
            Live
          </span>
          <span style={styles.pending}>
            Pending
            <strong style={styles.pendingValue}>{liveCount ?? 0}</strong>
          </span>
        </div>
      </header>

      <Dashboard />
    </div>
  )
}

export default function App() {
  // Treat presence of stored credentials as "logged in" for this session.
  const [authed, setAuthed] = useState(() => !!sessionStorage.getItem('dlqAuth'))

  if (!authed) {
    return <Login onSuccess={() => setAuthed(true)} />
  }

  return (
    <QueryClientProvider client={queryClient}>
      <DlqProvider>
        <AppInner />
      </DlqProvider>
    </QueryClientProvider>
  )
}

const MONO = 'ui-monospace, "SF Mono", "Roboto Mono", monospace'

const styles: Record<string, React.CSSProperties> = {
  app: {
    fontFamily: 'system-ui, -apple-system, "Segoe UI", sans-serif',
    minHeight: '100vh',
    backgroundColor: '#eef0f2',
  },
  topbar: {
    height: 48,
    backgroundColor: '#2b3138',
    color: '#fff',
    padding: '0 18px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottom: '1px solid #1f242a',
  },
  brandWrap: {
    display: 'flex',
    alignItems: 'baseline',
    gap: 10,
    minWidth: 0,
  },
  brand: {
    fontSize: 16,
    fontWeight: 700,
    letterSpacing: '-0.01em',
  },
  tagline: {
    fontSize: 12,
    color: '#9aa4af',
    whiteSpace: 'nowrap',
  },
  meta: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    flexShrink: 0,
  },
  live: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 7,
    fontSize: 13,
    color: '#cfd5db',
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    background: '#3ecf76',
    boxShadow: '0 0 0 3px rgba(62,207,118,0.18)',
  },
  pending: {
    fontSize: 13,
    color: '#cfd5db',
  },
  pendingValue: {
    fontFamily: MONO,
    color: '#fff',
    marginLeft: 6,
  },
}