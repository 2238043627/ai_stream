<template>
  <div class="app-container">
    <div class="chat-container">
      <div class="chat-header">
        <h1>智谱大模型对话</h1>
      </div>

      <div class="chat-messages" ref="messagesContainer">
        <div
            v-for="(message, index) in messages"
            :key="index"
            :class="['message', message.type]"
        >
          <div class="message-label">{{ message.type === 'user' ? '你' : 'AI' }}</div>
          <div class="message-content">{{ message.content }}</div>
        </div>

        <!-- 正在流式输出 -->
        <div v-if="isLoading" class="message assistant">
          <div class="message-label">AI</div>
          <div class="message-content">
            {{ currentResponse }}
            <span class="cursor">▋</span>
          </div>
        </div>
      </div>

      <div class="chat-input-container">
        <textarea
            v-model="inputMessage"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.enter.shift.exact="newLine"
            placeholder="输入消息，按Enter发送，Shift+Enter换行"
            class="chat-input"
            rows="3"
            :disabled="isLoading"
        ></textarea>

        <button
            @click="sendMessage"
            :disabled="isLoading || !inputMessage.trim()"
            class="send-button"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "ChatApp",

  data() {
    return {
      inputMessage: "",
      messages: [],
      isLoading: false,
      currentResponse: "",
    };
  },

  methods: {
    // 换行
    newLine() {
      this.inputMessage += "\n";
    },

    // 发送消息
    async sendMessage() {
      if (!this.inputMessage.trim() || this.isLoading) return;

      const userMessage = this.inputMessage.trim();

      // 追加用户消息
      this.messages.push({
        type: "user",
        content: userMessage,
      });

      this.inputMessage = "";
      this.isLoading = true;
      this.currentResponse = "";

      this.$nextTick(() => {
        this.scrollToBottom();
      });

      try {
        await this.startStream(userMessage);
      } catch (e) {
        console.error("流式请求失败:", e);
        this.currentResponse = "请求失败：" + e.message;
      } finally {
        this.isLoading = false;

        // 把完整 AI 回复加入历史消息
        this.messages.push({
          type: "assistant",
          content: this.currentResponse,
        });

        this.currentResponse = "";

        this.$nextTick(() => {
          this.scrollToBottom();
        });
      }
    },

    // 发起 SSE 流式请求
    async startStream(message) {
      const response = await fetch("http://localhost:8080/api/chat/stream", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
        },
        body: JSON.stringify({
          message: message,
        }),
      });

      if (!response.ok) {
        throw new Error("网络请求失败");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");

      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // SSE 按 \n\n 分隔
        const parts = buffer.split("\n\n");
        buffer = parts.pop(); // 剩余半包

        for (const part of parts) {
          this.handleSSEChunk(part);
        }

        this.$nextTick(() => {
          this.scrollToBottom();
        });
      }
    },

    // 解析 SSE 数据块
    handleSSEChunk(chunk) {
      const lines = chunk.split("\n");

      for (const line of lines) {
        if (!line.startsWith("data:")) continue;

        const data = line.replace("data:", "").trim();

        if (data === "[DONE]") {
          return;
        }

        try {
          const json = JSON.parse(data);
          const delta = json.choices?.[0]?.delta?.content;

          if (delta) {
            this.currentResponse += delta;
          }
        } catch (e) {
          console.warn("JSON解析失败:", data);
        }
      }
    },

    // 自动滚动到底部
    scrollToBottom() {
      const container = this.$refs.messagesContainer;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    },
  },
};
</script>

<style>
/* 样式保持你原来的不变 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

.app-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
}

.chat-container {
  width: 100%;
  max-width: 800px;
  height: 80vh;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  text-align: center;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message.user {
  align-items: flex-end;
}

.message.assistant {
  align-items: flex-start;
}

.message-label {
  font-size: 12px;
  font-weight: 600;
  color: #666;
  padding: 0 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  word-wrap: break-word;
  white-space: pre-wrap;
  line-height: 1.6;
}

.message.user .message-content {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-content {
  background: #f0f0f0;
  color: #333;
  border-bottom-left-radius: 4px;
}

.cursor {
  display: inline-block;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.chat-input-container {
  padding: 20px;
  border-top: 1px solid #e0e0e0;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  resize: none;
}

.send-button {
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}
</style>
