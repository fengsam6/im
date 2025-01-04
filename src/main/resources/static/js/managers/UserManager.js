const UserManager = {
    currentUser: null,
    selectedUser: null,

    async login(username) {
        if (!username || username.trim() === '') {
            alert('Please enter a username');
            return false;
        }

        try {
            // 先连接 WebSocket
            await WebSocketManager.connect(username);
            
            // 连接成功后再设置用户信息
            this.currentUser = username.trim();
            localStorage.setItem('currentUser', this.currentUser);
            
            // 显示聊天面板
            UIManager.showChatPanel(username);
            return true;
        } catch (error) {
            console.error('Login failed:', error);
            alert('Failed to connect to server');
            return false;
        }
    },

    logout() {
        if (!this.currentUser) return;

        try {
            WebSocketManager.disconnect();
            localStorage.removeItem('currentUser');
            this.currentUser = null;
            this.selectedUser = null;
            UIManager.resetUI();
        } catch (error) {
            console.error('Error during logout:', error);
        }
    },

    async loadLoginState() {
        const savedUser = localStorage.getItem('currentUser');
        if (savedUser) {
            try {
                const success = await this.login(savedUser);
                return success;
            } catch (error) {
                console.error('Error restoring session:', error);
                localStorage.removeItem('currentUser');
                return false;
            }
        }
        return false;
    },

    async selectUser(username) {
        this.selectedUser = username;
        document.getElementById('chatWith').textContent = `Chat with ${username}`;
        document.getElementById('messageInput').disabled = false;
        document.querySelector('.send-button').disabled = false;
        
        // 加载聊天历史
        await MessageManager.loadChatHistory(this.currentUser, username);
    }
}; 