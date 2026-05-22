import axios from 'axios'
import type { DlqMessage, ReplayAuditLog, ReplayResponse, Stats } from '../types/index'

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    Authorization: 'Basic ' + btoa('admin:admin')
  }
})

// Stats
export const fetchStats = async (): Promise<Stats> => {
  const res = await api.get('/stats')
  return res.data
}

// Messages
export const fetchAllMessages = async (): Promise<DlqMessage[]> => {
  const res = await api.get('/messages')
  return res.data
}

export const fetchMessagesByGroup = async (groupKey: string): Promise<DlqMessage[]> => {
  const res = await api.get(`/messages/group/${groupKey}`)
  return res.data
}

// Replay
export const replayMessage = async (
  id: string,
  targetDestination: string,
  dryRun: boolean
): Promise<ReplayResponse> => {
  const res = await api.post(`/replay/message/${id}`, null, {
    params: { targetDestination, dryRun }
  })
  return res.data
}

export const replayGroup = async (
  groupKey: string,
  targetDestination: string,
  dryRun: boolean
): Promise<ReplayResponse> => {
  const res = await api.post(`/replay/group/${groupKey}`, null, {
    params: { targetDestination, dryRun }
  })
  return res.data
}

export const discardMessage = async (id: string): Promise<void> => {
  await api.post(`/replay/discard/${id}`)
}

// Audit log
export const fetchAuditLog = async (): Promise<ReplayAuditLog[]> => {
  const res = await api.get('/replay/audit')
  return res.data
}