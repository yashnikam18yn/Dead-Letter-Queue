import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { WebSocketMessage } from '../types'

interface UseWebSocketProps {
  onMessage: (msg: WebSocketMessage) => void
}

export const useWebSocket = ({ onMessage }: UseWebSocketProps) => {
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8081/ws'),
      onConnect: () => {
        console.log('WebSocket connected')

        client.subscribe('/topic/dlq-updates', (msg) => {
          const data: WebSocketMessage = JSON.parse(msg.body)
          onMessage(data)
        })

        client.subscribe('/topic/replay-status', (msg) => {
          const data: WebSocketMessage = JSON.parse(msg.body)
          onMessage(data)
        })
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected')
      },
      reconnectDelay: 5000,
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [])
}