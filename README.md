# Easy-AI Starter

> 一个 AI 驱动的多轮对话参数收集框架，让业务系统快速具备"对话式收集参数 → 自动执行业务动作"的能力。

## 目录

- [功能特性](#功能特性)
- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [核心注解](#核心注解)
- [完整示例](#完整示例)
- [配置说明](#配置说明)
- [高级功能](#高级功能)
- [内置功能](#内置功能)
- [API 接口](#api-接口)

---

## 功能特性

### 核心能力

| 功能 | 说明 |
|------|------|
| **意图识别** | LLM 优先 + 关键词降级的两级意图识别，自动判断用户想做什么 |
| **多轮参数收集** | 渐进式收集业务参数，缺什么问什么，支持多轮对话 |
| **字段校验** | 内置枚举校验、自定义校验器，实时反馈校验结果 |
| **前置依赖** | 字段间依赖关系，A 字段收集完成后才追问 B 字段 |
| **Action 执行** | 参数收集完成后自动执行业务动作，返回结果 |
| **PostAction** | Action 执行后的钩子（日志、通知、审计等），best-effort 模式 |
| **会话管理** | 会话绑定任务，支持重置、取消、超时清理 |
| **状态持久化** | 任务状态数据库持久化，重启可恢复，支持乐观锁并发控制 |

### 工程能力

| 功能 | 说明 |
|------|------|
| **多模型支持** | DeepSeek / Kimi / Doubao / OpenAI 兼容接口，可配置 fallback 链 |
| **限流熔断** | 滑动窗口限流 + 连续失败熔断，保护 LLM 调用 |
| **分布式锁** | 同一 session 并发请求互斥，防止状态冲突 |
| **雪花算法 ID** | 全局唯一任务 ID |
| **SSE 流式输出** | 支持 Server-Sent Events 流式返回 |
| **对话历史** | 滑动窗口保留最近 N 轮对话，提升多轮上下文理解 |
| **注解驱动** | `@AiTask` + `@AiField` 声明式配置，零 XML |
| **动态功能介绍** | 自动扫描所有 Action，生成功能清单，无需硬编码 |

---

## 架构概览

```
用户消息
   │
   ▼
┌─────────────────┐
│  意图识别引擎    │  LLM 分类 → 关键词降级
│  IntentEngine   │
└────────┬────────┘
         │ 识别出 taskType
         ▼
┌─────────────────┐
│  任务引擎        │  加载/创建任务状态
│  AiTaskEngine   │  提取字段 → 校验 → 追问/执行
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌────────┐
│ 字段提取 │ │ 字段校验 │  LLM 提取 → 枚举/自定义校验
│Extraction│ │Validation│
└────┬────┘ └────┬────┘
     │           │
     └─────┬─────┘
           │ 全部收集完成
           ▼
    ┌──────────────┐
    │  Action 执行  │  执行业务动作
    │  ActionExec  │
    └──────┬───────┘
           │
           ▼
    ┌──────────────┐
    │ PostAction   │  日志/通知/审计（best-effort）
    └──────────────┘
```

---

## 快速开始

### 1. Maven 依赖

将框架安装到本地 Maven 仓库：

```bash
cd easy-ai-v2
mvn clean install -DskipTests
```

在你的项目 `pom.xml` 中引入：

```xml
<dependency>
    <groupId>com.link</groupId>
    <artifactId>easy-ai-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
server:
  port: 8080

spring:
  application:
    name: your-app
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/your_db?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8
    username: root
    password: your-password
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml

# EasyAI 框架配置
easy-ai:
  enabled: true
  task-engine:
    enabled: true
    annotation:
      enabled: true   # 开启 @AiTask 注解扫描

# 大模型配置（三选一，通过 active 切换）
large-language-model:
  active: deepseek
  deepseek:
    api:
      key: sk-your-deepseek-key
      url: https://api.deepseek.com/chat/completions
      model: deepseek-chat
  kimi:
    api:
      key: sk-your-kimi-key
      url: https://api.moonshot.cn/v1/chat/completions
      model: kimi-k2-turbo-preview
  doubao:
    api:
      key: your-doubao-key
      url: https://ark.cn-beijing.volces.com/api/v3/chat/completions
      model: doubao-seed-1-6-251015
```

### 3. 启动类

框架通过 Spring Boot 自动配置生效，无需额外注解。启动应用后，Liquibase 会自动创建所需表结构。

---

## 核心注解

### @AiTask — 声明任务

标注在 DTO 类上，定义一个 AI 任务：

```java
@Data
@AiTask(
    type = "CREATE_ORDER",
    name = "创建订单",
    description = "通过对话收集订单信息并创建订单",
    action = CreateOrderAction.class,
    postActions = {"LOG"},
    keywords = {"创建订单", "下单", "我要买"},
    examples = {"我要创建一个订单", "帮我下单"}
)
public class CreateOrderTask {
    // 字段定义见 @AiField
}
```

| 属性 | 说明 |
|------|------|
| `type` | 任务唯一标识 |
| `name` | 任务名称（用户可见） |
| `description` | 任务描述 |
| `action` | 业务 Action 执行器类 |
| `postActions` | PostAction 名称列表 |
| `keywords` | 意图识别关键词 |
| `examples` | 用户表达方式示例（传给 LLM 辅助分类） |

### @AiField — 声明字段

标注在 DTO 字段上：

```java
@AiField(
    name = "订单类型",
    description = "订单的类型",
    required = true,
    order = 1
)
private String orderType;
```

| 属性 | 说明 |
|------|------|
| `name` | 字段名称（用户可见） |
| `description` | 字段描述（传给 LLM 辅助提取） |
| `required` | 是否必填，默认 true |
| `order` | 收集顺序，数字越小越先收集 |

### @AiValid — 字段校验

```java
@AiField(name = "手机号")
@AiValid(validator = PhoneValidator.class)
private String phone;
```

### @AiDependsOn — 前置依赖

```java
@AiField(name = "问题描述")
@AiDependsOn("ticketType")  // ticketType 收集完成后才追问
private String description;
```

### @AiAction — 业务动作

```java
@AiAction(
    value = "CREATE_ORDER_ACTION",
    name = "创建订单",
    description = "根据收集的参数创建订单",
    triggers = {"创建订单", "下单"}
)
public class CreateOrderAction implements ActionExecutor {
    @Override
    public ActionResult execute(ActionContext context) {
        Map<String, Object> params = context.getParameters();
        // 执行业务逻辑...
        return ActionResult.success("订单创建成功！订单号：" + orderNo, result);
    }
}
```

### @AiPostAction — 后置动作

```java
@AiPostAction("LOG")
public class LogPostAction implements PostActionExecutor {
    @Override
    public void execute(ActionContext context) {
        log.info("操作日志: taskId={}, params={}", context.getTaskId(), context.getParameters());
    }
}
```

---

## 完整示例

以"创建客服工单"场景为例：

### 1. 定义任务 DTO

```java
@Data
@AiTask(
    type = "CREATE_TICKET",
    name = "创建工单",
    description = "通过多轮对话收集工单信息并创建工单",
    action = CreateTicketAction.class,
    postActions = {"LOG"},
    keywords = {"创建工单", "投诉", "建议", "咨询"},
    examples = {"我要投诉", "帮我创建一个工单"}
)
public class CreateTicketTask {

    @AiField(name = "工单类型", description = "工单类型：咨询/投诉/建议", order = 1)
    private TicketType ticketType;  // 枚举字段自动校验

    @AiField(name = "客户姓名", order = 2)
    private String customerName;

    @AiField(name = "联系电话", order = 3)
    @AiValid(validator = PhoneValidator.class)
    private String phone;

    @AiField(name = "问题描述", order = 4)
    @AiDependsOn("ticketType")
    private String description;

    @AiField(name = "优先级", required = false, order = 5)
    private Priority priority;  // 非必填枚举
}
```

### 2. 定义枚举

```java
public enum TicketType {
    CONSULT("咨询"),
    COMPLAINT("投诉"),
    SUGGESTION("建议");

    private final String label;
    // 构造函数、getter...
}
```

### 3. 自定义校验器

```java
@Component
public class PhoneValidator implements FieldValidator {
    @Override
    public ValidationResult validate(Object value, Map<String, Object> context) {
        String phone = String.valueOf(value);
        if (phone.matches("^1[3-9]\\d{9}$")) {
            return ValidationResult.ok(phone);  // 可返回标准化后的值
        }
        return ValidationResult.fail("手机号格式不正确，请输入11位手机号");
    }
}
```

### 4. 定义 Action

```java
@AiAction(
    value = "CREATE_TICKET_ACTION",
    name = "创建工单",
    description = "根据收集的字段创建客服工单",
    triggers = {"创建工单", "我要投诉"}
)
public class CreateTicketAction implements ActionExecutor {
    @Override
    public ActionResult execute(ActionContext context) {
        Map<String, Object> params = context.getParameters();
        String ticketNo = "TK" + System.currentTimeMillis();
        // 调用业务 Service 创建工单...
        return ActionResult.success("工单已创建成功！工单编号：" + ticketNo, Map.of("ticketNo", ticketNo));
    }
}
```

### 5. 调用对话接口

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private AiChatService aiChatService;

    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> req) {
        String sessionId = req.get("sessionId");
        String message = req.get("message");

        TaskContext context = TaskContext.builder()
                .tenantId(1L)
                .sessionId(sessionId)
                .data(new HashMap<>())
                .build();

        ChatResponse response = aiChatService.chat(message, sessionId, context);
        return Map.of(
            "message", response.getMessage(),
            "completed", response.isCompleted(),
            "taskId", response.getTaskId()
        );
    }
}
```

### 6. 对话效果

```
用户: 我要投诉
AI: 好的，请问您的姓名是？
用户: 我叫张三
AI: 请提供您的联系电话
用户: 13800138000
AI: 请描述您的问题
用户: 问题是物流太慢了
AI: 工单已创建成功！工单编号：TK123456789
```

---

## 配置说明

### LLM 配置

```yaml
large-language-model:
  active: deepseek  # 当前激活的模型
  deepseek:
    api:
      key: sk-xxx
      url: https://api.deepseek.com/chat/completions
      model: deepseek-chat
```

> 支持 `large-language-model.{provider}.api.key/url/model` 旧格式，
> 也支持 `llm.providers.{provider}.api-key/endpoint/model` 新格式。

### 框架配置

```yaml
easy-ai:
  enabled: true
  task-engine:
    enabled: true
    annotation:
      enabled: true
      # base-packages: com.your.package  # 缺省扫描启动类所在包
    llm:
      max-retries: 3              # LLM 调用最大重试次数
      initial-backoff-ms: 1000    # 初始退避时间
      backoff-multiplier: 2.0     # 退避倍数
      fallback-models:            # fallback 模型链
        - kimi
        - doubao
    resilience:
      enabled: true
      rate-limit-per-second: 10                    # 每秒最大请求数
      rate-limit-window-seconds: 1                 # 限流窗口
      circuit-breaker-sliding-window-size: 5       # 连续失败多少次触发熔断
      circuit-breaker-wait-duration-in-open-state-seconds: 30  # 熔断持续时间
```

### 数据库表

框架启动时通过 Liquibase 自动创建以下表：

| 表名 | 说明 |
|------|------|
| `ai_chat_session` | 会话表，存储会话状态和任务绑定 |
| `ai_task_config` | 任务配置表（注解配置时 version 恒为 1） |
| `ai_task_lock` | 分布式锁表 |
| `ai_chat_session_task` | 任务状态表，存储完整 TaskState JSON |

---

## 高级功能

### 枚举字段自动转换

字段类型为枚举时，框架自动将用户输入的中文标签转换为枚举值：

```java
public enum Priority {
    HIGH("高"), MEDIUM("中"), LOW("低");
}

@AiField(name = "优先级")
private Priority priority;
// 用户说"高优先级" → 自动提取为 Priority.HIGH
```

### 指定任务类型

跳过意图识别，直接进入指定任务：

```java
ChatResponse response = aiChatService.chatWithTaskType(
    message, sessionId, "CREATE_TICKET", context);
```

### 会话管理

```java
// 查看会话状态
AiChatSession session = sessionManager.loadOrCreate(sessionId, tenantId);

// 重置会话
sessionManager.reset(sessionId);

// 清除任务绑定
sessionManager.clearTask(sessionId);
```

### 任务状态查询

```java
TaskState state = taskStateManager.load(taskId, "CREATE_TICKET", 1);
// state.getFields() → 各字段收集状态
// state.getStatus() → INITIALIZED / COLLECTING / COMPLETED / FAILED
```

### SSE 流式输出

框架内置 SSE 接口：

```
POST /easyai/engine/chat/stream
Content-Type: application/json

{
    "sessionId": "xxx",
    "message": "我要投诉"
}
```

返回 `text/event-stream` 流式响应。

### 自定义 LLM Provider

实现 `LLMProvider` 接口：

```java
public class MyLLMProvider implements LLMProvider {
    public MyLLMProvider(LLMConfig.ProviderConfig config) {
        // 从 config 获取 apiKey/endpoint/model
    }

    @Override
    public String chat(List<Message> messages, LLMConfig config) {
        // 调用你的 LLM API
    }

    @Override
    public String getName() {
        return "my-llm";
    }
}
```

配置中使用：

```yaml
llm:
  provider: my-llm
  providers:
    my-llm:
      api-key: xxx
      endpoint: https://your-llm-api.com
      model: your-model
```

---

## 内置功能

### 功能介绍（FEATURE_INTRO）

框架内置 `FEATURE_INTRO` 任务，当用户问"你能做什么"、"有什么功能"时自动触发。

**动态扫描**所有已注册的 `@AiAction`，读取 `name` / `description` / `triggers` 元信息，实时生成功能清单。新增业务动作时无需修改任何代码。

标注了 `hidden = true` 的 Action 不会出现在列表中。

```
用户: 你能做什么
AI: 本系统是一个 AI 驱动的业务助手，目前支持以下功能：
1. 创建工单：根据收集的字段创建客服工单
2. 创建订单：通过对话收集订单信息并创建订单

您可以直接告诉我要做什么，我会通过对话收集所需信息并自动执行。
```

---

## API 接口

### AiChatService

| 方法 | 说明 |
|------|------|
| `chat(message, sessionId, context)` | 自动意图识别 + 多轮对话 |
| `chatWithTaskType(message, sessionId, taskType, context)` | 指定任务类型，跳过意图识别 |

### ChatResponse

| 字段 | 说明 |
|------|------|
| `message` | 返回给用户的消息 |
| `taskId` | 任务 ID |
| `taskType` | 任务类型 |
| `completed` | 任务是否完成 |
| `needMore` | 是否需要更多信息 |
| `clarification` | 是否是澄清提问 |
| `actionResult` | Action 执行结果（完成时） |

---

## 常见问题

### Q: 意图识别不准确怎么办？

A: 可以在 `@AiTask` 的 `keywords` 和 `examples` 中补充更多用户表达方式。LLM 分类优先，关键词作为降级兜底。

### Q: 如何调试 LLM 调用？

A: 设置日志级别：
```yaml
logging:
  level:
    com.link.easyai: debug
```

### Q: 支持哪些数据库？

A: 目前支持 MySQL，通过 Liquibase 自动管理表结构。

### Q: 如何添加新的 LLM 模型？

A: 实现 `LLMProvider` 接口，或直接使用 `openai_compatible` 通用 Provider（只要提供 OpenAI 兼容的 `/chat/completions` 接口）。

---

## License

MIT License
