# 智谱大模型流式对话项目

本项目实现了调用大模型API进行流式对话的前后端分离应用。

## 项目结构

```
bigmodel_stream/
├── nailBooking/          # Spring Boot 后端项目
└── chat-frontend/        # Vue 3 前端项目
```

## 后端项目 (nailBooking)

### 技术栈
- Spring Boot 2.4.2
- Spring WebFlux (用于响应式流式处理)
- Java 8

### 配置说明

后端配置文件位于 `chatStream/src/main/resources/application.yaml`：

```yaml
server:
  port: 8080

ai:
  api-key: "your-api-key"  # 智谱API密钥
  base-url: ""
  model: "model"
```

### 运行后端

```bash
cd chatStream
mvn clean install
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动。

### API接口

- **POST** `/api/chat/stream`
  - 请求体：`{"message": "你的问题"}`
  - 响应：SSE流式输出 (text/event-stream)

## 前端项目 (chat-frontend)

### 技术栈
- Vue 3
- Vite
- Fetch API (用于接收SSE流)

### 运行前端

```bash
cd chat-frontend
npm install
npm run dev
```

前端应用将在 `http://localhost:3000` 启动。

### 功能特性

- ✅ 实时流式对话
- ✅ 自动滚动到最新消息
- ✅ 响应式设计

## 使用流程

1. 启动后端服务（确保在application.yaml中配置了正确的大模型API密钥）
2. 启动前端项目
3. 在浏览器中打开 `http://localhost:3000`
4. 输入消息并发送，即可看到AI的流式回复

## 注意事项

1. 确保在 `application.yaml` 中配置了正确的智谱API密钥
2. 确保后端服务运行在8080端口，前端在3000端口
3. 前端通过代理访问后端API，配置在 `chat-frontend/vite.config.js` 中

