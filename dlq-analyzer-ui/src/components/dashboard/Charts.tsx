import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  BarChart,
  Bar,
  Cell,
} from 'recharts'
import { fetchAllMessages } from '../../api/dlqApi'
import type { DlqMessage } from '../../types'

// ---- helpers --------------------------------------------------------------

// Bucket failures into hourly slots based on the best available timestamp.
function buildTimeline(messages: DlqMessage[]): { label: string; count: number }[] {
  if (messages.length === 0) return []

  const times = messages
    .map(m => m.firstFailedAt ?? m.createdAt ?? m.lastFailedAt)
    .filter(Boolean)
    .map(t => new Date(t as string).getTime())
    .filter(t => !Number.isNaN(t))

  if (times.length === 0) return []

  const HOUR = 60 * 60 * 1000
  const min = Math.min(...times)
  const max = Math.max(...times)

  // Floor the start to the top of its hour.
  const start = new Date(min)
  start.setMinutes(0, 0, 0)
  const startMs = start.getTime()

  // Build empty hourly buckets across the whole range (so gaps show as zero).
  const bucketCount = Math.max(1, Math.floor((max - startMs) / HOUR) + 1)
  const buckets = new Array(bucketCount).fill(0)

  times.forEach(t => {
    const idx = Math.floor((t - startMs) / HOUR)
    if (idx >= 0 && idx < bucketCount) buckets[idx]++
  })

  return buckets.map((count, i) => {
    const d = new Date(startMs + i * HOUR)
    const label = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    return { label, count }
  })
}

// Count failures by error class and keep the top N.
function buildErrorBreakdown(
  messages: DlqMessage[],
  topN = 6
): { name: string; count: number }[] {
  const map: Record<string, number> = {}
  messages.forEach(m => {
    const key = m.errorClass ?? 'Unknown Error'
    map[key] = (map[key] ?? 0) + 1
  })
  return Object.entries(map)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, topN)
}

// A restrained palette for the bar chart (muted, admin-tool friendly).
const BAR_COLORS = ['#b23b3b', '#bd6b2c', '#b7902f', '#5a8a4a', '#3f7c8c', '#6b6f9c']

// ---- component ------------------------------------------------------------

export default function Charts() {
  const { data: allMessages = [], isLoading } = useQuery({
    queryKey: ['messages'],
    queryFn: fetchAllMessages,
    refetchInterval: 30000,
  })

  const timeline = useMemo(() => buildTimeline(allMessages), [allMessages])
  const breakdown = useMemo(() => buildErrorBreakdown(allMessages), [allMessages])

  const hasData = allMessages.length > 0

  return (
    <div style={styles.row}>
      {/* Failures over time */}
      <section style={styles.panel}>
        <div style={styles.head}>
          <span style={styles.title}>Failures over time</span>
          <span style={styles.sub}>hourly</span>
        </div>
        <div style={styles.chartArea}>
          {isLoading ? (
            <div style={styles.state}>Loading…</div>
          ) : !hasData ? (
            <div style={styles.state}>No data to chart yet.</div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={timeline} margin={{ top: 8, right: 12, bottom: 0, left: -16 }}>
                <defs>
                  <linearGradient id="failFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#2c6fbb" stopOpacity={0.25} />
                    <stop offset="100%" stopColor="#2c6fbb" stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#eef0f2" vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 11, fill: '#8a929b' }}
                  tickLine={false}
                  axisLine={{ stroke: '#e3e6ea' }}
                  minTickGap={24}
                />
                <YAxis
                  allowDecimals={false}
                  tick={{ fontSize: 11, fill: '#8a929b' }}
                  tickLine={false}
                  axisLine={false}
                  width={36}
                />
                <Tooltip
                  contentStyle={tooltipStyle}
                  labelStyle={{ color: '#3a4047', fontWeight: 600 }}
                  formatter={(v) => [`${v}`, 'failures']}
                />
                <Area
                  type="monotone"
                  dataKey="count"
                  stroke="#2c6fbb"
                  strokeWidth={2}
                  fill="url(#failFill)"
                  dot={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>

      {/* Top error types */}
      <section style={styles.panel}>
        <div style={styles.head}>
          <span style={styles.title}>Top error types</span>
          <span style={styles.sub}>by count</span>
        </div>
        <div style={styles.chartArea}>
          {isLoading ? (
            <div style={styles.state}>Loading…</div>
          ) : !hasData ? (
            <div style={styles.state}>No data to chart yet.</div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart
                layout="vertical"
                data={breakdown}
                margin={{ top: 4, right: 16, bottom: 0, left: 8 }}
                barCategoryGap={6}
              >
                <CartesianGrid stroke="#eef0f2" horizontal={false} />
                <XAxis
                  type="number"
                  allowDecimals={false}
                  tick={{ fontSize: 11, fill: '#8a929b' }}
                  tickLine={false}
                  axisLine={{ stroke: '#e3e6ea' }}
                />
                <YAxis
                  type="category"
                  dataKey="name"
                  tick={{ fontSize: 11, fill: '#3a4047' }}
                  tickLine={false}
                  axisLine={false}
                  width={140}
                />
                <Tooltip
                  contentStyle={tooltipStyle}
                  cursor={{ fill: '#f4f5f7' }}
                  formatter={(v) => [`${v}`, 'messages']}
                />
                <Bar dataKey="count" radius={[0, 3, 3, 0]} maxBarSize={22}>
                  {breakdown.map((_, i) => (
                    <Cell key={i} fill={BAR_COLORS[i % BAR_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </div>
  )
}

const tooltipStyle: React.CSSProperties = {
  border: '1px solid #d7dbe0',
  borderRadius: 4,
  fontSize: 12,
  fontFamily: 'system-ui, sans-serif',
  boxShadow: '0 2px 6px rgba(16,24,40,0.08)',
}

const styles: Record<string, React.CSSProperties> = {
  row: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
    gap: 14,
    marginBottom: 20,
  },
  panel: {
    border: '1px solid #d7dbe0',
    borderRadius: 4,
    background: '#fff',
    fontFamily: 'system-ui, -apple-system, "Segoe UI", sans-serif',
  },
  head: {
    display: 'flex',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    padding: '10px 14px',
    borderBottom: '1px solid #e3e6ea',
    background: '#f7f8f9',
  },
  title: { fontSize: 13, fontWeight: 600, color: '#3a4047' },
  sub: { fontSize: 11, color: '#8a929b', textTransform: 'uppercase', letterSpacing: '0.04em' },
  chartArea: { padding: '12px 10px' },
  state: {
    height: 200,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#8a929b',
    fontSize: 13,
  },
}