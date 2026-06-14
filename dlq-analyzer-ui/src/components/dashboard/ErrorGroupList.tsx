import { useQuery } from '@tanstack/react-query'
import { fetchAllMessages } from '../../api/dlqApi'
import { useDlqContext } from '../../context/DlqContext'
import type { DlqMessage, ErrorGroup } from '../../types'

// Group messages by groupKey on the frontend
function groupMessages(messages: DlqMessage[]): ErrorGroup[] {
  const map: Record<string, DlqMessage[]> = {}
  messages.forEach(msg => {
    if (!msg.groupKey) return
    if (!map[msg.groupKey]) map[msg.groupKey] = []
    map[msg.groupKey].push(msg)
  })
  return Object.entries(map).map(([groupKey, msgs]) => ({
    groupKey,
    errorClass: msgs[0].errorClass ?? 'Unknown Error',
    count: msgs.length,
    messages: msgs,
  }))
}

export default function ErrorGroupList() {
  const { selectedGroup, setSelectedGroup, setMessages } = useDlqContext()

  const { data: allMessages = [], isLoading } = useQuery({
    queryKey: ['messages'],
    queryFn: fetchAllMessages,
    refetchInterval: 30000,
  })

  const groups = groupMessages(allMessages.filter(m => m.status === 'PENDING'))

  const handleGroupClick = (group: ErrorGroup) => {
    setSelectedGroup(group)
    setMessages(group.messages)
  }

  return (
    <div style={styles.panel}>
      <div style={styles.header}>
        <span style={styles.title}>Error Groups</span>
        <span style={styles.count}>{isLoading ? '' : `${groups.length}`}</span>
      </div>

      {isLoading && <div style={styles.state}>Loading…</div>}

      {!isLoading && groups.length === 0 && (
        <div style={styles.state}>No pending error groups.</div>
      )}

      {!isLoading && groups.length > 0 && (
        <div>
          <div style={styles.columnHead}>
            <span>Error class</span>
            <span>Messages</span>
          </div>

          {groups.map(group => {
            const selected = selectedGroup?.groupKey === group.groupKey
            return (
              <div
                key={group.groupKey}
                onClick={() => handleGroupClick(group)}
                style={{
                  ...styles.row,
                  ...(selected ? styles.rowSelected : {}),
                }}
                onMouseEnter={e => {
                  if (!selected) (e.currentTarget as HTMLDivElement).style.background = '#f5f6f7'
                }}
                onMouseLeave={e => {
                  if (!selected) (e.currentTarget as HTMLDivElement).style.background = 'transparent'
                }}
              >
                <div style={styles.rowMain}>
                  <div style={styles.errorClass}>{group.errorClass}</div>
                  <div style={styles.groupKey}>{group.groupKey.substring(0, 12)}…</div>
                </div>
                <div style={styles.countCell}>{group.count}</div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  panel: {
    border: '1px solid #d7dbe0',
    borderRadius: 4,
    backgroundColor: '#fff',
    minHeight: 500,
    fontFamily: 'system-ui, -apple-system, "Segoe UI", sans-serif',
    fontSize: 13,
    color: '#2a2f36',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '10px 12px',
    borderBottom: '1px solid #e3e6ea',
    background: '#f7f8f9',
  },
  title: {
    fontSize: 13,
    fontWeight: 600,
    color: '#3a4047',
  },
  count: {
    fontFamily: 'ui-monospace, "SF Mono", "Roboto Mono", monospace',
    fontSize: 12,
    color: '#6b7280',
    background: '#eceef1',
    borderRadius: 3,
    padding: '1px 7px',
    minWidth: 18,
    textAlign: 'center',
  },
  state: {
    padding: '14px 12px',
    color: '#6b7280',
    fontSize: 13,
  },
  columnHead: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '6px 12px',
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: '0.04em',
    color: '#8a929b',
    borderBottom: '1px solid #eceef1',
  },
  row: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    padding: '9px 12px',
    borderBottom: '1px solid #eef0f2',
    borderLeft: '3px solid transparent',
    cursor: 'pointer',
    background: 'transparent',
  },
  rowSelected: {
    background: '#eef4fb',
    borderLeft: '3px solid #2c6fbb',
  },
  rowMain: {
    minWidth: 0,
  },
  errorClass: {
    fontWeight: 600,
    color: '#b23b3b',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  groupKey: {
    fontFamily: 'ui-monospace, "SF Mono", "Roboto Mono", monospace',
    fontSize: 11,
    color: '#8a929b',
    marginTop: 2,
  },
  countCell: {
    fontFamily: 'ui-monospace, "SF Mono", "Roboto Mono", monospace',
    fontSize: 13,
    fontWeight: 600,
    color: '#3a4047',
    whiteSpace: 'nowrap',
  },
}