export interface DlqMessage {
  id: string
  messageId: string
  brokerType: 'RABBITMQ' | 'KAFKA'
  sourceDestination: string
  payload: string
  headers: string
  errorClass: string
  errorMessage: string
  stackTrace: string
  groupKey: string
  failureCount: number
  firstFailedAt: string
  lastFailedAt: string
  status: 'PENDING' | 'REPLAYED' | 'DISCARDED'
  createdAt: string
}

export interface ErrorGroup {
  groupKey: string
  errorClass: string
  count: number
  messages: DlqMessage[]
}

export interface Stats {
  total: number
  pending: number
  replayed: number
  discarded: number
}

export interface ReplayAuditLog {
  id: string
  batchId: string
  action: 'REPLAY' | 'DRY_RUN' | 'DISCARD'
  targetDestination: string
  performedBy: string
  result: 'SUCCESS' | 'FAILED'
  errorDetail: string
  performedAt: string
}

export interface ReplayResponse {
  batchId: string
  status: string
}

export interface WebSocketMessage {
  event: 'NEW_MESSAGES' | 'REPLAY_COMPLETE'
  totalPending?: number
  batchId?: string
  count?: number
}