import { useQuery } from '@tanstack/react-query'
import { fetchStats } from '../../api/dlqApi'

type Metric = {
  key: 'total' | 'pending' | 'replayed' | 'discarded'
  label: string
  hint: string
  accent: string
  tint: string
}

const METRICS: Metric[] = [
  { key: 'total',     label: 'Total in DLQ', hint: 'All captured failures', accent: '#3B4252', tint: '#EDEFF4' },
  { key: 'pending',   label: 'Pending',      hint: 'Awaiting action',       accent: '#B7791F', tint: '#FBF3E2' },
  { key: 'replayed',  label: 'Replayed',     hint: 'Sent back successfully', accent: '#2F855A', tint: '#E7F4EC' },
  { key: 'discarded', label: 'Discarded',    hint: 'Dismissed by operator',  accent: '#718096', tint: '#EEF1F4' },
]

export default function MetricsBar() {
  const { data: stats, isLoading } = useQuery({
    queryKey: ['stats'],
    queryFn: fetchStats,
    refetchInterval: 30000,
  })

  return (
    <section style={styles.wrap} aria-label="Queue overview">
      <header style={styles.head}>
        <span style={styles.eyebrow}>Queue health</span>
        <span style={styles.live}>
          <span style={styles.dot} />
          Updates every 30s
        </span>
      </header>

      <div style={styles.grid}>
        {METRICS.map((m) => {
          const value = stats?.[m.key] ?? 0
          const emphasize = m.key === 'pending' && value > 0
          return (
            <article
              key={m.key}
              style={{
                ...styles.card,
                borderTopColor: m.accent,
                background: emphasize ? m.tint : '#FFFFFF',
              }}
            >
              <div style={styles.label}>{m.label}</div>
              <div style={{ ...styles.value, color: m.accent }}>
                {isLoading ? <span style={styles.skeleton} /> : value.toLocaleString()}
              </div>
              <div style={styles.hint}>{m.hint}</div>
            </article>
          )
        })}
      </div>
    </section>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: {
    marginBottom: 24,
    fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
  },
  head: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  eyebrow: {
    fontSize: 12,
    fontWeight: 600,
    letterSpacing: '0.08em',
    textTransform: 'uppercase',
    color: '#4A5568',
  },
  live: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    fontSize: 12,
    color: '#718096',
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: '50%',
    background: '#2F855A',
    boxShadow: '0 0 0 3px rgba(47,133,90,0.15)',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
    gap: 12,
  },
  card: {
    borderTop: '3px solid',
    borderRadius: 10,
    padding: '16px 18px',
    boxShadow: '0 1px 2px rgba(16,24,40,0.06), 0 1px 3px rgba(16,24,40,0.10)',
    transition: 'background 200ms ease',
  },
  label: {
    fontSize: 13,
    fontWeight: 600,
    color: '#2D3748',
    marginBottom: 8,
  },
  value: {
    fontFamily: '"SF Mono", "Roboto Mono", ui-monospace, monospace',
    fontSize: 32,
    lineHeight: 1,
    fontWeight: 700,
    fontVariantNumeric: 'tabular-nums',
    marginBottom: 6,
  },
  hint: {
    fontSize: 12,
    color: '#A0AEC0',
  },
  skeleton: {
    display: 'inline-block',
    width: 28,
    height: 28,
    borderRadius: 6,
    background: 'linear-gradient(90deg,#EDF2F7 25%,#E2E8F0 50%,#EDF2F7 75%)',
    backgroundSize: '200% 100%',
  },
}