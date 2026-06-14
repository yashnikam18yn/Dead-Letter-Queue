import { useDlqContext } from '../../context/DlqContext'
import { replayGroup, discardMessage } from '../../api/dlqApi'
import { useState } from 'react'

export default function MessageDetail() {
  const { messages, selectedGroup, setMessages } = useDlqContext()
  const [replayStatus, setReplayStatus] = useState<string | null>(null)
  const [statusKind, setStatusKind] = useState<'info' | 'ok' | 'error'>('info')

  // Derive the replay target from the group's source queue instead of hardcoding.
  // Convention: strip a trailing ".dlq" / "-dlq" / ".dead-letter" to get the live queue.
  const deriveTarget = (): string => {
    const source = messages[0]?.sourceDestination ?? ''
    return source
      .replace(/\.dlq$/i, '')
      .replace(/-dlq$/i, '')
      .replace(/\.dead-letter$/i, '')
  }

  const handleReplay = async (dryRun: boolean) => {
    if (!selectedGroup) return
    const target = deriveTarget()
    try {
      setStatusKind('info')
      setReplayStatus(dryRun ? 'Running dry run…' : `Replaying to "${target}"…`)
      const res = await replayGroup(selectedGroup.groupKey, target, dryRun)
      setStatusKind('ok')
      setReplayStatus(
        dryRun
          ? `Dry run complete — batch ${res.batchId}`
          : `Replayed to "${target}" — batch ${res.batchId}`
      )
    } catch (e) {
      setStatusKind('error')
      setReplayStatus('Replay failed. Check the backend logs.')
    }
  }

  const handleDiscard = async (id: string) => {
    try {
      await discardMessage(id)
      setMessages(messages.filter(m => m.id !== id))
    } catch (e) {
      setStatusKind('error')
      setReplayStatus('Discard failed.')
    }
  }

  if (messages.length === 0) {
    return (
      <div style={styles.panel}>
        <div style={styles.header}>
          <span style={styles.title}>Message Details</span>
        </div>
        <div style={styles.empty}>Select an error group on the left to view its messages.</div>
      </div>
    )
  }

  return (
    <div style={styles.panel}>
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.titleWrap}>
          <span style={styles.errorClass}>{selectedGroup?.errorClass ?? 'Message Details'}</span>
          <span style={styles.count}>{messages.length} messages</span>
        </div>
        <div style={styles.actions}>
          <button style={styles.btnSecondary} onClick={() => handleReplay(true)}>
            Dry run
          </button>
          <button style={styles.btnPrimary} onClick={() => handleReplay(false)}>
            Replay all
          </button>
        </div>
      </div>

      {/* Replay status */}
      {replayStatus && (
        <div
          style={{
            ...styles.status,
            ...(statusKind === 'ok'
              ? styles.statusOk
              : statusKind === 'error'
              ? styles.statusError
              : styles.statusInfo),
          }}
        >
          {replayStatus}
        </div>
      )}

      {/* Message list */}
      <div style={styles.body}>
        {messages.map(message => (
          <div key={message.id} style={styles.msgCard}>
            <div style={styles.msgBar}>
              <span style={styles.msgId}>{message.messageId ?? message.id}</span>
              <button style={styles.btnDiscard} onClick={() => handleDiscard(message.id)}>
                Discard
              </button>
            </div>

            {/* Two-column property grid */}
            <div style={styles.grid}>
              <Field label="ID" value={message.id} mono />
              <Field label="Message ID" value={message.messageId} mono />
              <Field label="Broker Type" value={message.brokerType} />
              <Field label="Source Queue" value={message.sourceDestination} mono />
              <Field label="Status" value={message.status} />
              <Field label="Error Class" value={message.errorClass} />
              <Field label="Failure Count" value={message.failureCount} />
              <Field label="Group Key" value={message.groupKey} mono />
              <Field label="Created At" value={message.createdAt} />
              <Field label="First Failed At" value={message.firstFailedAt} />
              <Field label="Last Failed At" value={message.lastFailedAt} />
              <Field label="Error Message" value={message.errorMessage} span2 />
            </div>

            {/* Payload + stack trace side by side */}
            <div style={styles.codeRow}>
              <div style={styles.codeCol}>
                <div style={styles.sectionLabel}>Payload</div>
                <pre style={styles.pre}>{message.payload || 'No payload'}</pre>
              </div>
              <div style={styles.codeCol}>
                <div style={styles.sectionLabel}>Stack trace</div>
                <pre style={{ ...styles.pre, whiteSpace: 'pre-wrap' }}>
                  {message.stackTrace || 'No stack trace available'}
                </pre>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

interface FieldProps {
  label: string
  value: any
  mono?: boolean
  span2?: boolean
}

function Field({ label, value, mono, span2 }: FieldProps) {
  return (
    <div style={{ ...styles.field, ...(span2 ? styles.fieldSpan2 : {}) }}>
      <span style={styles.fieldLabel}>{label}</span>
      <span style={{ ...styles.fieldValue, ...(mono ? styles.fieldMono : {}) }}>
        {value ?? '—'}
      </span>
    </div>
  )
}

const MONO = 'ui-monospace, "SF Mono", "Roboto Mono", monospace'

const styles: Record<string, React.CSSProperties> = {
  panel: {
    border: '1px solid #d7dbe0',
    borderRadius: 4,
    backgroundColor: '#fff',
    fontFamily: 'system-ui, -apple-system, "Segoe UI", sans-serif',
    fontSize: 14,
    color: '#2a2f36',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    padding: '10px 14px',
    borderBottom: '1px solid #e3e6ea',
    background: '#f7f8f9',
  },
  titleWrap: { display: 'flex', alignItems: 'baseline', gap: 10, minWidth: 0 },
  title: { fontSize: 15, fontWeight: 600, color: '#3a4047' },
  errorClass: {
    fontSize: 15,
    fontWeight: 600,
    color: '#b23b3b',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  count: { fontSize: 13, color: '#6b7280', whiteSpace: 'nowrap' },
  actions: { display: 'flex', gap: 8, flexShrink: 0 },
  btnSecondary: {
    padding: '6px 14px',
    background: '#fff',
    border: '1px solid #c4cad1',
    borderRadius: 3,
    color: '#3a4047',
    fontSize: 13,
    cursor: 'pointer',
  },
  btnPrimary: {
    padding: '6px 14px',
    background: '#2c6fbb',
    border: '1px solid #2c6fbb',
    borderRadius: 3,
    color: '#fff',
    fontSize: 13,
    cursor: 'pointer',
  },
  status: {
    margin: '10px 14px 0',
    padding: '8px 12px',
    borderRadius: 3,
    fontSize: 13,
    fontFamily: MONO,
  },
  statusInfo: { background: '#eef4fb', border: '1px solid #cfe0f2', color: '#2c5d92' },
  statusOk: { background: '#edf7f0', border: '1px solid #c6e6d2', color: '#2f6f4d' },
  statusError: { background: '#fbeeee', border: '1px solid #f0cccc', color: '#9d3a3a' },
  body: { padding: 12 },
  msgCard: {
    border: '1px solid #e3e6ea',
    borderRadius: 4,
    marginBottom: 12,
    overflow: 'hidden',
  },
  msgBar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '6px 12px',
    background: '#f7f8f9',
    borderBottom: '1px solid #eceef1',
  },
  msgId: { fontFamily: MONO, fontSize: 12, color: '#8a929b' },
  btnDiscard: {
    padding: '4px 12px',
    background: '#fff',
    border: '1px solid #d8a3a3',
    borderRadius: 3,
    color: '#a33a3a',
    fontSize: 13,
    cursor: 'pointer',
  },
  // Two-column grid; each field is label + value on one line to stay compact
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
    columnGap: 18,
    padding: '8px 14px',
  },
  field: {
    display: 'flex',
    alignItems: 'baseline',
    gap: 8,
    padding: '4px 0',
    borderBottom: '1px solid #f4f5f7',
    minWidth: 0,
  },
  fieldSpan2: { gridColumn: '1 / -1' },
  fieldLabel: {
    fontSize: 12.5,
    color: '#8a929b',
    flexShrink: 0,
    width: 104,
  },
  fieldValue: {
    fontSize: 13.5,
    color: '#2a2f36',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  fieldMono: { fontFamily: MONO, fontSize: 12.5 },
  // Payload and stack trace placed side by side to cut height
  codeRow: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 12,
    padding: '4px 12px 12px',
  },
  codeCol: { minWidth: 0 },
  sectionLabel: {
    fontSize: 11.5,
    textTransform: 'uppercase',
    letterSpacing: '0.04em',
    color: '#8a929b',
    marginBottom: 4,
  },
  pre: {
    background: '#f6f7f8',
    border: '1px solid #e3e6ea',
    borderRadius: 3,
    padding: 10,
    margin: 0,
    fontFamily: MONO,
    fontSize: 12.5,
    lineHeight: 1.45,
    color: '#3a4047',
    overflow: 'auto',
    maxHeight: 150,
  },
  empty: { padding: '14px', color: '#6b7280', fontSize: 14 },
}