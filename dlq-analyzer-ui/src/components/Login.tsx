import { useState } from 'react'
import { setCredentials, clearCredentials, fetchStats } from '../api/dlqApi'

interface LoginProps {
  onSuccess: () => void
}

export default function Login({ onSuccess }: LoginProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    setError('')
    setLoading(true)
    // Store credentials, then make one authenticated call to verify them.
    setCredentials(username, password)
    try {
      await fetchStats()
      onSuccess()
    } catch {
      clearCredentials()
      setError('Invalid username or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 320, margin: '120px auto', fontFamily: 'system-ui, sans-serif' }}>
      <h2 style={{ marginBottom: 16 }}>DLQ Analyzer — Sign in</h2>
      <input
        style={{ display: 'block', width: '100%', padding: 8, marginBottom: 8, boxSizing: 'border-box' }}
        placeholder="Username"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
      />
      <input
        style={{ display: 'block', width: '100%', padding: 8, marginBottom: 12, boxSizing: 'border-box' }}
        type="password"
        placeholder="Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
      />
      <button
        style={{ width: '100%', padding: 10, cursor: 'pointer' }}
        onClick={handleSubmit}
        disabled={loading || !username || !password}
      >
        {loading ? 'Signing in…' : 'Sign in'}
      </button>
      {error && <p style={{ color: '#c0392b', marginTop: 12 }}>{error}</p>}
    </div>
  )
}