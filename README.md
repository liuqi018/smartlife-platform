# SmartLife Platform

SmartLife Platform 是基于 Java 8、Spring Boot 2.7、MyBatis-Plus、MySQL、Redis 和 Redisson 的本地生活服务项目，包含商户、探店笔记、优惠券、用户关系以及故障演练能力。

## 本地生活推荐 Agent

`POST /api/agent/chat` 提供登录用户可用的自然语言推荐入口。Agent 通过模型 Function Calling 调用本地只读工具，最终回答只能依据数据库与 Redis 返回的真实数据。

架构调用链：

`AgentController -> AgentService（Function Calling 循环） -> AiModelClient / AgentToolRegistry -> 现有 Shop、Blog、Voucher Service`

- `AiModelClient` 隔离模型供应商协议；当前实现使用 Java 8 兼容的 Spring `RestTemplate` 调用 Responses API。
- `AgentToolRegistry` 只注册白名单只读工具，并在执行前校验工具名、JSON 参数、类型和范围。
- 附近商户沿用现有 Redis GEO 数据和距离排序逻辑。
- Redis 使用 `agent:conversation:{conversationId}` 保存最近对话，默认 60 分钟过期、最多 20 条消息，并校验会话所属用户。
- 每轮返回工具调用摘要和真实查询到的商户精简 DTO，不向模型暴露完整 Entity。

### 配置

必须通过环境变量提供以下值，API Key 没有配置文件默认值，也不会写入日志：

```powershell
$env:AI_BASE_URL="https://api.openai.com/v1"
$env:AI_API_KEY="your-api-key"
$env:AI_MODEL="function-calling-model"
mvn spring-boot:run
```

可在 `application.yaml` 调整：

- `ai.enabled`：是否启用 Agent。
- `ai.max-tool-rounds`：最大工具调用轮数，默认 5。
- `ai.timeout-seconds`：模型 HTTP 超时，默认 30 秒。
- `ai.conversation-ttl-minutes`：会话过期时间，默认 60 分钟。
- `ai.max-conversation-messages`：最多保存的对话消息数，默认 20。

MySQL、Redis 的连接配置仍使用项目现有 `spring.datasource` 和 `spring.redis` 配置。完整应用启动时两者均需可用。

### 调用示例

Agent 接口沿用现有登录机制，请把 `/user/login` 返回的 token 放入 `authorization` 请求头：

```bash
curl -X POST http://localhost:8081/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "authorization: LOGIN_TOKEN" \
  -d '{
    "message": "推荐附近适合两个人吃饭的火锅店，预算 150 元",
    "longitude": 121.4737,
    "latitude": 31.2304,
    "conversationId": null
  }'
```

成功响应的 `data` 包含：

- `conversationId`：后续对话传回此值。
- `answer`：模型基于工具数据生成的回答。
- `recommendedShops`：本轮工具实际查询到的商户。
- `toolCalls`：执行过的工具名、参数、状态和结果数量。

### 支持的只读工具

| 工具 | 能力 |
| --- | --- |
| `search_nearby_shops` | 按商户类型、经纬度、距离和分页查询 Redis GEO |
| `get_shop_detail` | 通过现有商户缓存逻辑查询指定商户 |
| `search_shop_blogs` | 按商户 ID 或关键词查询探店笔记 |
| `get_available_vouchers` | 查询商户有效优惠券，并过滤不可用秒杀券 |

### 安全限制

- Agent 必须登录；`conversationId` 与当前用户绑定，不能访问其他用户会话。
- 用户消息最多 2000 字；经度限制为 -180～180，纬度限制为 -90～90。
- 最多执行配置的工具轮数，未知工具和非法参数会被拒绝。
- 工具为空时返回无结果提示，不采用模型虚构的推荐。
- 不提供点赞、关注、领券、秒杀、下单、上传、故障注入或任何修改数据的工具。
- Redis 不可用时返回可定位的会话存储错误；模型未配置时明确指出缺少的环境变量。

## 启动

```bash
docker compose up -d
```

或在本机准备 MySQL、Redis 和上述 AI 环境变量后运行：

```bash
mvn spring-boot:run
```

## 测试

运行全部测试：

```bash
mvn test
```

只运行 Agent 测试：

```bash
mvn "-Dtest=AgentControllerValidationTest,AgentToolRegistryTest,AgentServiceTest,ResponsesApiModelClientTest" test
```

测试使用 Mock `AiModelClient`，不会访问外部模型。

## 监控与故障演练

项目保留 Actuator、Prometheus、AlertManager 以及 CPU、JVM OOM、MySQL/Redis 等故障演练能力。Agent 不会调用任何故障注入接口。
