import MetricsBar from '../components/dashboard/MetricsBar'
import ErrorGroupList from '../components/dashboard/ErrorGroupList'
import MessageDetail from '../components/dashboard/MessageDetail'

export default function Dashboard() {
  return (
    <div style={{ padding: '16px' }}>

      {/* Stats row */}
      <MetricsBar />

      {/* Main panels */}
      <div style={{ display: 'flex', gap: '16px', marginTop: '16px' }}>

        {/* Left panel — error groups */}
        <div style={{ width: '35%', minWidth: '280px' }}>
          <ErrorGroupList />
        </div>

        {/* Right panel — message detail */}
        <div style={{ flex: 1 }}>
          <MessageDetail />
        </div>

      </div>
    </div>
  )
}