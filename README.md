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
| `get_my_shop_order_overview` | 当前登录经营者店铺的代金券订单数量、当前状态、创建趋势和上一等长周期对比 |
| `get_my_shop_voucher_ranking` | 当前登录经营者店铺的代金券订单排行和各券当前状态数量 |

### 商家端代金券订单分析

先在当前数据库执行 `src/main/resources/db/shop_owner_order_analysis.sql`。该脚本只创建独立的 `tb_shop_owner` 表，不修改原有店铺或订单。第一版一个账号只绑定一家店铺；未绑定的账号调用两个统计工具会得到明确错误。归属只能由可信的数据库管理操作指定，不提供用户自行认领店铺的 API。例如，在确认用户和店铺身份后：

```sql
INSERT INTO tb_shop_owner (shop_id, owner_user_id)
SELECT 15, id FROM tb_user WHERE phone = '已验证的商家手机号';
```

请先确认该手机号对应且仅对应目标账号、店铺 15 确实是其经营店铺；演示可使用现有短信验证码登录创建账号。然后由另一个测试用户通过已有 `POST /voucher-order/normal/{voucherId}` 购买该店铺的一张普通券，例如初始化数据中店铺 15 的券 2；需要测试已支付状态时再调用现有 `POST /voucher-order/{orderId}/mock-pay`。这只是模拟支付，不代表真实收款。秒杀订单也会按其券所属店铺计入统计。

登录已绑定的账号后，可以在现有 `POST /agent/chat` 中提问“本周店铺代金券订单与上一等长时段相比怎么样？”、“最近 30 天订单趋势怎么样？”或“哪个优惠券产生的订单最多？”。模型只选择工具并解释结果；工具参数不接受用户或店铺 ID，后端从登录身份和 `tb_shop_owner` 确定访问范围。订单按创建时间归入期间，状态是查询时的当前状态，数据只覆盖平台内代金券订单。两个工具不提供营业额、销售额、净收入或财务指标。

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
