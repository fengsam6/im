const UIManager = {
    showChatPanel(username) {
        document.getElementById('loginPanel').style.display = 'none';
        document.getElementById('currentUserName').textContent = username;
        this.setCurrentUserAvatar(username);
        document.getElementById('chatPanel').style.display = 'grid';
        this.updateConnectionStatus('connected');
    },

    updateConnectionStatus(status) {
        const statusDiv = document.querySelector('.user-status');
        if (!statusDiv) return;

        const statusConfig = {
            connected: { icon: 'fa-circle', text: 'Online', color: '#00C853' },
            disconnected: { icon: 'fa-circle', text: 'Offline', color: '#FF3D00' },
            connecting: { icon: 'fa-circle', text: 'Connecting...', color: '#FFC107' }
        };

        const config = statusConfig[status];
        if (config) {
            statusDiv.innerHTML = `<i class="fas ${config.icon}"></i> ${config.text}`;
            statusDiv.style.color = config.color;
        }
    },

    addMessage(message, isSent = true) {
        if (document.querySelector(`[data-message-id="${message.messageId}"]`)) {
            return;
        }

        const messageArea = document.getElementById('messageArea');
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isSent ? 'sent' : 'received'} message-new`;
        messageDiv.setAttribute('data-message-id', message.messageId);
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = message.content;
        messageDiv.appendChild(contentDiv);

        const timeDiv = document.createElement('div');
        timeDiv.className = 'time';
        timeDiv.textContent = this.formatTime(message.timestamp);
        messageDiv.appendChild(timeDiv);

        if (isSent) {
            const statusDiv = document.createElement('div');
            statusDiv.className = 'message-status sending';
            statusDiv.innerHTML = `
                <span class="status-text">发送中</span>
                <i class="status-icon fas fa-circle-notch fa-spin"></i>
            `;
            messageDiv.appendChild(statusDiv);
        }

        messageArea.appendChild(messageDiv);
        this.scrollToBottom();

        setTimeout(() => {
            messageDiv.classList.remove('message-new');
        }, 300);
    },

    updateMessageStatus(messageId, status) {
        const messageDiv = document.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) return;

        const statusDiv = messageDiv.querySelector('.message-status');
        if (!statusDiv) return;

        statusDiv.classList.remove('sending', 'sent', 'delivered', 'read', 'failed');
        
        const statusConfig = {
            SENDING: {
                class: 'sending',
                text: '发送中',
                icon: 'fa-circle-notch fa-spin'
            },
            SENT: {
                class: 'sent',
                text: '已发送',
                icon: 'fa-check'
            },
            DELIVERED: {
                class: 'delivered',
                text: '已送达',
                icon: 'fa-check-double'
            },
            FAILED: {
                class: 'failed',
                text: '发送失败，点击重试',
                icon: 'fa-exclamation-circle'
            }
        };

        const config = statusConfig[status];
        if (config) {
            statusDiv.classList.add(config.class);
            statusDiv.innerHTML = `
                <span class="status-text">${config.text}</span>
                <i class="status-icon fas ${config.icon}"></i>
            `;

            if (status === 'FAILED') {
                statusDiv.onclick = () => {
                    MessageManager.resendMessage(messageId);
                };
            } else {
                statusDiv.onclick = null;
            }
        }
    },

    scrollToBottom() {
        const messageArea = document.getElementById('messageArea');
        messageArea.scrollTop = messageArea.scrollHeight;
    },

    showSystemMessage(text) {
        const messageArea = document.getElementById('messageArea');
        const systemDiv = document.createElement('div');
        systemDiv.className = 'system-message';
        systemDiv.textContent = text;
        messageArea.appendChild(systemDiv);
        this.scrollToBottom();

        setTimeout(() => {
            systemDiv.remove();
        }, 5000);
    },

    setCurrentUserAvatar(username) {
        const avatar = document.getElementById('currentUserAvatar');
        if (avatar) {
            avatar.textContent = username.charAt(0).toUpperCase();
        }
    },

    resetUI() {
        document.getElementById('loginPanel').style.display = 'flex';
        document.getElementById('chatPanel').style.display = 'none';
        document.getElementById('username').value = '';
        document.getElementById('messageArea').innerHTML = '';
        document.getElementById('userList').innerHTML = '';
        document.getElementById('chatWith').textContent = 'Select a user';
        document.getElementById('messageInput').disabled = true;
        document.querySelector('.send-button').disabled = true;
        
        this.updateConnectionStatus('disconnected');
    },

    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const isToday = date.toDateString() === now.toDateString();
        
        if (isToday) {
            return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }
        return date.toLocaleDateString([], { month: 'short', day: 'numeric' }) + 
               ' ' + 
               date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    },

    clearMessages() {
        const messageArea = document.getElementById('messageArea');
        messageArea.innerHTML = '';
    },

    addHistoricalMessage(message, isSent) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isSent ? 'sent' : 'received'}`;
        messageDiv.setAttribute('data-message-id', message.messageId);
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = message.content;
        messageDiv.appendChild(contentDiv);

        const timeDiv = document.createElement('div');
        timeDiv.className = 'time';
        timeDiv.textContent = this.formatTime(message.timestamp);
        messageDiv.appendChild(timeDiv);

        if (isSent) {
            const statusDiv = document.createElement('div');
            statusDiv.className = 'message-status';
            messageDiv.appendChild(statusDiv);
            this.updateMessageStatus(message.messageId, message.status);
        }

        const messageArea = document.getElementById('messageArea');
        messageArea.appendChild(messageDiv);
    }
}; 