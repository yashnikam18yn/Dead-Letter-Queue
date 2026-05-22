import { useQuery } from '@tanstack/react-query'
import { fetchStats } from '../../api/dlqApi'

export default function MetricsBar() {

  const { data: stats, isLoading } = useQuery({
    queryKey: ['stats'],
    queryFn: fetchStats,
    refetchInterval: 30000,
  })

  if (isLoading) {
    return <div>Loading metrics...</div>
  }

  return (
    <div
      style={{
        border: '1px solid #ccc',
        padding: '12px',
        marginBottom: '20px',
        backgroundColor: '#f5f5f5'
      }}
    >
      <h3>Queue Overview</h3>

      <table
        style={{
          width: '100%',
          borderCollapse: 'collapse'
        }}
      >
        <thead>
          <tr>
            <th style={tableHeader}>Metric</th>
            <th style={tableHeader}>Count</th>
          </tr>
        </thead>

        <tbody>
          <tr>
            <td style={tableCell}>Total in DLQ</td>
            <td style={tableCell}>{stats?.total ?? 0}</td>
          </tr>

          <tr>
            <td style={tableCell}>Pending</td>
            <td style={tableCell}>{stats?.pending ?? 0}</td>
          </tr>

          <tr>
            <td style={tableCell}>Replayed</td>
            <td style={tableCell}>{stats?.replayed ?? 0}</td>
          </tr>

          <tr>
            <td style={tableCell}>Discarded</td>
            <td style={tableCell}>{stats?.discarded ?? 0}</td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}

const tableHeader = {
  border: '1px solid #999',
  padding: '8px',
  textAlign: 'left' as const,
  backgroundColor: '#e0e0e0'
}

const tableCell = {
  border: '1px solid #999',
  padding: '8px'
}