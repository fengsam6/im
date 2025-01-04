const Config = {
    // WebSocket 配置
    ws: {
        heartbeatInterval: 30000,  // 30秒
        heartbeatTimeout: 90000,   // 90秒
        reconnectInterval: 3000,   // 3秒
        maxReconnectAttempts: 5
    },
    
    // 消息配置
    message: {
        maxRetries: 3,
        retryInterval: 3000,
        batchSize: 10,
        timeout: 300000  // 5分钟
    },
    
    // API 端点
    api: {
        chatHistory: '/api/chat/history',
        messages: '/api/chat/messages',
        unread: '/api/chat/unread'
    }
}; 