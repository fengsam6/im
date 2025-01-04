// 页面加载完成后执行
document.addEventListener('DOMContentLoaded', async function() {
    // 尝试恢复登录状态
    const loginSuccess = await UserManager.loadLoginState();
    if (!loginSuccess) {
        // 如果没有登录状态，显示登录面板
        document.getElementById('loginPanel').style.display = 'flex';
        document.getElementById('chatPanel').style.display = 'none';
    }
});

// 登录函数
async function login() {
    const usernameInput = document.getElementById('username');
    const username = usernameInput.value.trim();
    
    if (!username) {
        alert('Please enter a username');
        return;
    }

    try {
        const success = await UserManager.login(username);
        if (!success) {
            alert('Login failed');
        }
    } catch (error) {
        console.error('Login error:', error);
        alert('Login failed');
    }
}

// 登出函数
function logout() {
    UserManager.logout();
}

// 发送消息函数
function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const content = messageInput.value.trim();
    
    if (!content || !UserManager.selectedUser) {
        return;
    }

    const message = {
        type: 'CHAT',
        from: UserManager.currentUser,
        to: UserManager.selectedUser,
        content: content,
        timestamp: Date.now(),
        needAck: true
    };

    // 发送消息
    const messageId = MessageManager.sendMessage(message);
    if (messageId) {
        message.messageId = messageId;
        // 立即显示消息
        UIManager.addMessage(message, true);
        // 清空输入框
        messageInput.value = '';
        // 滚动到底部
        UIManager.scrollToBottom();
    }
}

// 处理回车键发送消息
function handleKeyPress(event) {
    if (event.key === 'Enter') {
        if (event.target.id === 'username') {
            login();
        } else if (event.target.id === 'messageInput') {
            sendMessage();
        }
    }
}

// 添加用户搜索功能
document.getElementById('userSearch').addEventListener('input', function(e) {
    const searchTerm = e.target.value.toLowerCase();
    const userItems = document.querySelectorAll('.user-item');
    
    userItems.forEach(item => {
        const username = item.querySelector('.user-name').textContent.toLowerCase();
        item.style.display = username.includes(searchTerm) ? 'flex' : 'none';
    });
}); 