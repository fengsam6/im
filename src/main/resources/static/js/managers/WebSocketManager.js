const WebSocketManager = {
    ws: null,
    reconnectAttempts: 0,
    maxReconnectAttempts: 5,
    reconnectInterval: 3000,
    heartbeatInterval: 30000,
    heartbeatTimer: null,
    lastHeartbeatResponse: Date.now(),
    heartbeatTimeout: 90000,
    
    connect(username) {
        this.username = username;
        return new Promise((resolve, reject) => {
            try {
                const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                const wsUrl = `${protocol}//${window.location.host}/ws?username=${encodeURIComponent(username)}`;
                
                this.ws = new WebSocket(wsUrl);
                
                this.ws.onopen = () => {
                    console.log('WebSocket connected');
                    UIManager.updateConnectionStatus('connected');
                    this.reconnectAttempts = 0;
                    this.startHeartbeat();
                    resolve();
                };
                
                this.ws.onmessage = (event) => {
                    try {
                        const message = JSON.parse(event.data);
                        console.log('Received message:', message);
                        this.lastHeartbeatResponse = Date.now();
                        MessageHandler.handleMessage(event);
                    } catch (error) {
                        console.error('Error handling message:', error);
                    }
                };
                
                this.ws.onclose = () => {
                    console.log('WebSocket disconnected');
                    UIManager.updateConnectionStatus('disconnected');
                    this.stopHeartbeat();
                    this.reconnect();
                };
                
                this.ws.onerror = (error) => {
                    console.error('WebSocket error:', error);
                    reject(error);
                };
            } catch (error) {
                console.error('Error creating WebSocket:', error);
                reject(error);
            }
        });
    },
    
    sendMessage(message) {
        if (this.ws?.readyState === WebSocket.OPEN) {
            try {
                // 如果是聊天消息，确保有消息ID
                if (message.type === 'CHAT' && !message.messageId) {
                    message.messageId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
                }
                
                const messageStr = JSON.stringify(message);
                console.log('Sending message:', messageStr);
                this.ws.send(messageStr);
                return true;
            } catch (error) {
                console.error('Error sending message:', error);
                return false;
            }
        } else {
            console.warn('WebSocket is not connected');
            UIManager.updateConnectionStatus('disconnected');
            return false;
        }
    },
    
    resendMessage(messageId) {
        // 获取原始消息
        const messageDiv = document.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) return;
        
        const content = messageDiv.querySelector('.message-content').textContent;
        const to = UserManager.selectedUser;
        
        if (!to) {
            console.error('No recipient selected');
            return;
        }
        
        // 创建新消息
        const message = {
            type: 'CHAT',
            messageId: messageId,
            from: this.username,
            to: to,
            content: content,
            timestamp: Date.now(),
            needAck: true
        };
        
        // 发送消息
        if (this.sendMessage(message)) {
            UIManager.updateMessageStatus(messageId, 'SENDING');
        } else {
            UIManager.updateMessageStatus(messageId, 'FAILED');
        }
    },
    
    startHeartbeat() {
        this.stopHeartbeat();
        
        this.heartbeatTimer = setInterval(() => {
            if (this.ws?.readyState === WebSocket.OPEN) {
                // 检查上次心跳响应时间
                if (Date.now() - this.lastHeartbeatResponse > this.heartbeatTimeout) {
                    console.log('Heartbeat timeout, reconnecting...');
                    this.ws.close();
                    return;
                }
                
                // 发送心跳
                this.sendMessage({
                    type: 'HEARTBEAT',
                    from: this.username,
                    timestamp: Date.now()
                });
            }
        }, this.heartbeatInterval);
    },
    
    stopHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    },
    
    disconnect() {
        this.stopHeartbeat();
        if (this.ws) {
            if (this.ws.readyState === WebSocket.OPEN) {
                this.sendMessage({
                    type: 'LOGOUT',
                    from: this.username,
                    timestamp: Date.now()
                });
            }
            this.ws.close();
            this.ws = null;
        }
    }
}; 