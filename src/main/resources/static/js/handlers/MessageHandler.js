const MessageHandler = {
    handleMessage(event) {
        try {
            const message = JSON.parse(event.data);
            console.log('Received message:', message);
            WebSocketManager.lastHeartbeatResponse = Date.now();

            switch (message.type) {
                case 'CHAT':
                    this.handleChatMessage(message);
                    break;
                case 'ACK':
                    this.handleAck(message);
                    break;
                case 'BATCH_ACK':
                    this.handleBatchAck(message);
                    break;
                case 'USER_LIST':
                    this.handleUserList(message);
                    break;
                case 'LOGIN':
                    this.handleUserLogin(message);
                    break;
                case 'LOGOUT':
                    this.handleUserLogout(message);
                    break;
                case 'HEARTBEAT':
                    // 心跳消息不需要特殊处理
                    break;
                default:
                    console.warn('Unknown message type:', message.type);
            }
        } catch (error) {
            console.error('Error handling message:', error);
        }
    },

    handleChatMessage(message) {
        console.log('Handling chat message:', message);
        // 检查消息是否已经显示过
        if (!document.querySelector(`[data-message-id="${message.messageId}"]`)) {
            // 判断是发送还是接收的消息
            const isSent = message.from === UserManager.currentUser;
            
            // 显示消息
            UIManager.addMessage(message, isSent);
            
            // 如果是接收到的消息，发送确认
            if (!isSent && message.needAck) {
                const ackMessage = {
                    type: 'ACK',
                    ackMessageId: message.messageId,
                    from: UserManager.currentUser,
                    to: message.from,
                    timestamp: Date.now(),
                    status: 'DELIVERED'
                };
                WebSocketManager.sendMessage(ackMessage);
            }
        }
    },

    handleAck(message) {
        console.log('Received ACK message:', message);
        // 更新消息状态
        if (message.status === 'DELIVERED') {
            UIManager.updateMessageStatus(message.ackMessageId, 'DELIVERED');
        } else if (message.status === 'SENT') {
            UIManager.updateMessageStatus(message.ackMessageId, 'SENT');
        } else if (message.status === 'FAILED') {
            UIManager.updateMessageStatus(message.ackMessageId, 'FAILED');
        }
    },

    handleBatchAck(message) {
        if (message.batchAckMessageIds) {
            message.batchAckMessageIds.forEach(messageId => {
                UIManager.updateMessageStatus(messageId, 'DELIVERED');
            });
        }
    },

    handleUserList(message) {
        const userList = document.getElementById('userList');
        userList.innerHTML = '';
        
        message.users.forEach(username => {
            if (username !== UserManager.currentUser) {
                const userDiv = document.createElement('div');
                userDiv.className = 'user-item';
                userDiv.setAttribute('data-username', username);
                userDiv.innerHTML = `
                    <div class="user-avatar">${username.charAt(0).toUpperCase()}</div>
                    <div class="user-name">${username}</div>
                `;
                userDiv.onclick = () => UserManager.selectUser(username);
                userList.appendChild(userDiv);
            }
        });
    },

    handleUserLogin(message) {
        if (message.from === UserManager.currentUser) return;

        const userList = document.getElementById('userList');
        if (!userList.querySelector(`.user-item[data-username="${message.from}"]`)) {
            const userDiv = document.createElement('div');
            userDiv.className = 'user-item';
            userDiv.setAttribute('data-username', message.from);
            userDiv.innerHTML = `
                <div class="user-avatar">${message.from.charAt(0).toUpperCase()}</div>
                <div class="user-name">${message.from}</div>
            `;
            userDiv.onclick = () => UserManager.selectUser(message.from);
            userList.appendChild(userDiv);
        }
        UIManager.showSystemMessage(`${message.from} has joined`);
    },

    handleUserLogout(message) {
        if (message.from === UserManager.currentUser) return;

        const userItem = document.querySelector(`.user-item[data-username="${message.from}"]`);
        if (userItem) {
            userItem.remove();
        }

        if (UserManager.selectedUser === message.from) {
            UserManager.selectedUser = null;
            document.getElementById('chatWith').textContent = 'Select a user';
            document.getElementById('messageInput').disabled = true;
            document.querySelector('.send-button').disabled = true;
        }

        UIManager.showSystemMessage(`${message.from} has left`);
    }
}; 