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
    messages: msgs
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

console.log('isLoading:', isLoading)
console.log('allMessages:', allMessages)
console.log('groups:', groups)
console.log('statuses:', allMessages.map(m => m.status))


  const handleGroupClick = (group: ErrorGroup) => {
    setSelectedGroup(group)
    setMessages(group.messages)
  }

  if (isLoading) {
    return (
      <div style={{ border: '1px solid #ccc', padding: '12px', minHeight: '500px', backgroundColor: '#fff' }}>
        <h3>Error Groups</h3>
        <p>Loading...</p>
      </div>
    )
  }

  return (
    <div style={{ border: '1px solid #ccc', padding: '12px', minHeight: '500px', backgroundColor: '#fff' }}>
      <h3 style={{ marginTop: 0 }}>
        Error Groups
        <span style={{ fontSize: '13px', color: '#666', marginLeft: '8px' }}>
          ({groups.length} groups)
        </span>
      </h3>

      {groups.length === 0 && (
        <p style={{ color: '#666' }}>No pending error groups. All clear!</p>
      )}

      {groups.map(group => (
        <div
          key={group.groupKey}
          onClick={() => handleGroupClick(group)}
          style={{
            border: selectedGroup?.groupKey === group.groupKey
              ? '2px solid #1976d2'
              : '1px solid #ccc',
            padding: '10px 12px',
            marginBottom: '10px',
            cursor: 'pointer',
            backgroundColor: selectedGroup?.groupKey === group.groupKey
              ? '#e3f2fd'
              : '#fafafa',
          }}
        >
          {/* Error class name */}
          <div style={{ fontWeight: 'bold', fontSize: '14px', color: '#c62828', marginBottom: '4px' }}>
            {group.errorClass}
          </div>

          {/* Count badge + group key */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', color: '#666' }}>
              GroupKey: {group.groupKey.substring(0, 12)}...
            </span>
            <span style={{
              backgroundColor: '#ff5252',
              color: '#fff',
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '12px',
              fontWeight: 'bold'
            }}>
              {group.count} messages
            </span>
          </div>
        </div>
      ))}
    </div>
  )
}