const MessageManager = {
    pendingMessages: new Map(),
    messageQueue: [],
    retryCount: 3,
    retryInterval: 3000,
    batchSize: 10,
    pendingAcks: [],

    sendMessage(message) {
        if (message.messageId == null) {
            message.messageId = this.generateMessageId();
        }
        message.timestamp = Date.now();
        message.needAck = true;
        message.status = 'SENDING';

        console.log("Sending message:", message);

        // 存储消息到待确认列表
        this.pendingMessages.set(message.messageId, {
            message,
            retries: 0,
            timestamp: Date.now()
        });

        try {
            // 发送消息
            WebSocketManager.sendMessage(message);
            
            // 启动重试计时器
            this.scheduleRetry(message.messageId);
            
            return message.messageId;
        } catch (error) {
            console.error("Failed to send message:", message.messageId, error);
            this.pendingMessages.delete(message.messageId);
            UIManager.updateMessageStatus(message.messageId, 'FAILED');
            return null;
        }
    },

    handleAck(ackMessage) {
        if (ackMessage.batchAckMessageIds) {
            // 处理批量确认
            ackMessage.batchAckMessageIds.forEach(messageId => {
                this.confirmMessage(messageId);
            });
        } else if (ackMessage.ackMessageId) {
            // 处理单条确认
            this.confirmMessage(ackMessage.ackMessageId);
        }
    },

    confirmMessage(messageId) {
        const pendingMessage = this.pendingMessages.get(messageId);
        if (pendingMessage) {
            // 清除重试计时器
            if (pendingMessage.retryTimer) {
                clearTimeout(pendingMessage.retryTimer);
            }
            
            // 从待确认列表中移除
            this.pendingMessages.delete(messageId);
            
            // 更新消息状态
            UIManager.updateMessageStatus(messageId, 'DELIVERED');
        }
    },

    handleIncomingMessage(message) {
        // 显示接收到的消息
        UIManager.addMessage(message, false);

        if (message.needAck) {
            // 将确认消息添加到队列
            this.pendingAcks.push(message.messageId);
            
            // 如果队列达到批量大小或者是第一个确认，启动批量发送
            if (this.pendingAcks.length >= this.batchSize) {
                this.sendBatchAck();
            } else if (this.pendingAcks.length === 1) {
                setTimeout(() => this.sendBatchAck(), 1000);
            }
        }
    },

    // 添加批量确认发送方法
    sendBatchAck() {
        if (this.pendingAcks.length === 0) return;

        const ackMessage = {
            type: 'BATCH_ACK',
            from: UserManager.currentUser,
            batchAckMessageIds: [...this.pendingAcks],
            timestamp: Date.now()
        };

        if (WebSocketManager.sendMessage(ackMessage)) {
            this.pendingAcks = []; // 只有在发送成功时才清空队列
        }
    },

    scheduleRetry(messageId) {
        const pendingMessage = this.pendingMessages.get(messageId);
        if (!pendingMessage) return;

        // 清除之前的重试计时器（如果存在）
        if (pendingMessage.retryTimer) {
            clearTimeout(pendingMessage.retryTimer);
        }

        pendingMessage.retryTimer = setTimeout(() => {
            if (this.pendingMessages.has(messageId)) {
                if (pendingMessage.retries < this.retryCount) {
                    pendingMessage.retries++;
                    console.log(`Retrying message ${messageId} (attempt ${pendingMessage.retries})`);
                    
                    // 更新UI状态为重新发送中
                    UIManager.updateMessageStatus(messageId, 'SENDING');
                    
                    // 重新发送消息
                    WebSocketManager.sendMessage(pendingMessage.message);
                    
                    // 安排下一次重试
                    this.scheduleRetry(messageId);
                } else {
                    console.log(`Message ${messageId} failed after ${this.retryCount} attempts`);
                    this.pendingMessages.delete(messageId);
                    UIManager.updateMessageStatus(messageId, 'FAILED');
                    UIManager.showSystemMessage('Message delivery failed');
                }
            }
        }, this.retryInterval * Math.pow(2, pendingMessage.retries)); // 使用指数退避
    },

    generateMessageId() {
        return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    },

    cleanupOldMessages() {
        const now = Date.now();
        const timeout = 5 * 60 * 1000; // 5分钟超时

        for (const [messageId, data] of this.pendingMessages) {
            if (now - data.timestamp > timeout) {
                this.pendingMessages.delete(messageId);
                UIManager.updateMessageStatus(messageId, 'FAILED');
            }
        }
    },

    async loadChatHistory(user1, user2) {
        try {
            const response = await fetch(`/api/chat/history?user1=${encodeURIComponent(user1)}&user2=${encodeURIComponent(user2)}`);
            if (!response.ok) {
                throw new Error('Failed to load chat history');
            }
            
            const messages = await response.json();
            UIManager.clearMessages();
            
            messages.forEach(message => {
                const isSent = message.from === UserManager.currentUser;
                UIManager.addMessage({
                    messageId: message.messageId,
                    from: message.from,
                    to: message.to,
                    content: message.content,
                    timestamp: message.timestamp,
                    status: message.status
                }, isSent);
            });
            
            UIManager.scrollToBottom();
        } catch (error) {
            console.error('Error loading chat history:', error);
            UIManager.showSystemMessage('Failed to load chat history');
        }
    },

    async loadUnreadMessages() {
        try {
            const response = await fetch(`/api/chat/unread?username=${encodeURIComponent(UserManager.currentUser)}`);
            if (!response.ok) {
                throw new Error('Failed to load unread messages');
            }
            
            const messages = await response.json();
            return messages;
        } catch (error) {
            console.error('Error loading unread messages:', error);
            return [];
        }
    },

    resendMessage(messageId) {
        const pendingMessage = this.pendingMessages.get(messageId);
        if (pendingMessage) {
            // 重置重试次数
            pendingMessage.retries = 0;
            pendingMessage.timestamp = Date.now();
            
            // 更新UI状态
            UIManager.updateMessageStatus(messageId, 'SENDING');
            
            // 重新发送消息
            WebSocketManager.sendMessage(pendingMessage.message);
            
            // 重新安排重试
            this.scheduleRetry(messageId);
        } else {
            // 如果消息不在待确认列表中，创建新的发送请求
            const messageDiv = document.querySelector(`[data-message-id="${messageId}"]`);
            if (messageDiv) {
                const content = messageDiv.querySelector('.message-content').textContent;
                const message = {
                    type: 'CHAT',
                    messageId: messageId,
                    from: UserManager.currentUser,
                    to: UserManager.selectedUser,
                    content: content,
                    timestamp: Date.now(),
                    needAck: true
                };
                
                this.sendMessage(message);
            }
        }
    }
}; 