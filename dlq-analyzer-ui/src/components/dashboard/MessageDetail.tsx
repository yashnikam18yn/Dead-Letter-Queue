import { useDlqContext } from '../../context/DlqContext'
import { replayGroup, discardMessage } from '../../api/dlqApi'
import { useState } from 'react'

export default function MessageDetail() {
  const { messages, selectedGroup, setMessages } = useDlqContext()
  const [replayStatus, setReplayStatus] = useState<string | null>(null)

  const handleReplay = async (dryRun: boolean) => {
    if (!selectedGroup) return
    try {
      setReplayStatus(dryRun ? 'Running dry run...' : 'Replaying...')
      const res = await replayGroup(selectedGroup.groupKey, 'orders', dryRun)
      setReplayStatus(dryRun
        ? `Dry run done — batchId: ${res.batchId}`
        : `Replayed successfully — batchId: ${res.batchId}`
      )
    } catch (e) {
      setReplayStatus('Failed. Check logs.')
    }
  }

  const handleDiscard = async (id: string) => {
    try {
      await discardMessage(id)
      setMessages(messages.filter(m => m.id !== id))
    } catch (e) {
      alert('Discard failed')
    }
  }

  if (messages.length === 0) {
    return (
      <div style={{
        border: '1px solid #ccc',
        backgroundColor: '#fff',
        padding: '12px',
        minHeight: '500px'
      }}>
        <h3>Message Details</h3>
        <p>Select an error group from the left to see messages</p>
      </div>
    )
  }

  return (
    <div style={{
      border: '1px solid #ccc',
      backgroundColor: '#fff',
      padding: '12px',
      minHeight: '500px',
      overflow: 'auto'
    }}>

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
        <h3 style={{ margin: 0 }}>
          {selectedGroup?.errorClass ?? 'Message Details'}
          <span style={{ fontSize: '14px', color: '#666', marginLeft: '8px' }}>
            ({messages.length} messages)
          </span>
        </h3>

        {/* Replay buttons */}
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={() => handleReplay(true)}
            style={{ padding: '6px 12px', backgroundColor: '#e0e0e0', border: '1px solid #999', cursor: 'pointer' }}
          >
            Dry Run
          </button>
          <button
            onClick={() => handleReplay(false)}
            style={{ padding: '6px 12px', backgroundColor: '#1976d2', color: '#fff', border: 'none', cursor: 'pointer' }}
          >
            Replay All
          </button>
        </div>
      </div>

      {/* Replay status */}
      {replayStatus && (
        <div style={{ marginBottom: '12px', padding: '8px', backgroundColor: '#e8f5e9', border: '1px solid #a5d6a7' }}>
          {replayStatus}
        </div>
      )}

      {/* Message list */}
      {messages.map((message) => (
        <div key={message.id} style={{
          border: '1px solid #999',
          marginBottom: '20px',
          padding: '12px',
          backgroundColor: '#fafafa'
        }}>

          {/* Discard button per message */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '8px' }}>
            <button
              onClick={() => handleDiscard(message.id)}
              style={{ padding: '4px 10px', backgroundColor: '#ff5252', color: '#fff', border: 'none', cursor: 'pointer' }}
            >
              Discard
            </button>
          </div>

          <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: '20px' }}>
            <tbody>
              <Row label="ID" value={message.id} />
              <Row label="Message ID" value={message.messageId} />
              <Row label="Broker Type" value={message.brokerType} />
              <Row label="Source Queue" value={message.sourceDestination} />
              <Row label="Status" value={message.status} />
              <Row label="Error Class" value={message.errorClass} />
              <Row label="Error Message" value={message.errorMessage} />
              <Row label="Failure Count" value={message.failureCount} />
              <Row label="Created At" value={message.createdAt} />
              <Row label="First Failed At" value={message.firstFailedAt} />
              <Row label="Last Failed At" value={message.lastFailedAt} />
              <Row label="Group Key" value={message.groupKey} />
            </tbody>
          </table>

          <div style={{ marginBottom: '20px' }}>
            <h4>Payload</h4>
            <pre style={{ backgroundColor: '#f5f5f5', border: '1px solid #ccc', padding: '10px', overflow: 'auto' }}>
              {message.payload || 'No Payload'}
            </pre>
          </div>

          <div>
            <h4>Stack Trace</h4>
            <pre style={{ backgroundColor: '#f5f5f5', border: '1px solid #ccc', padding: '10px', overflow: 'auto', whiteSpace: 'pre-wrap' }}>
              {message.stackTrace || 'No stack trace available'}
            </pre>
          </div>

        </div>
      ))}
    </div>
  )
}

interface RowProps {
  label: string
  value: any
}

function Row({ label, value }: RowProps) {
  return (
    <tr>
      <td style={{ border: '1px solid #ccc', padding: '8px', fontWeight: 'bold', width: '220px', backgroundColor: '#f0f0f0' }}>
        {label}
      </td>
      <td style={{ border: '1px solid #ccc', padding: '8px' }}>
        {value ?? '-'}
      </td>
    </tr>
  )
}