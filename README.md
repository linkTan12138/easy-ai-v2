# Easy-AI Starter

> 一个 AI 驱动的多轮对话参数收集框架，让业务系统快速具备"对话式收集参数 → 自动执行业务任务"的能力。
> 注解定义任务结构与字段，数据库仅做字段提取提示词的热更新覆盖，支持全局 + 租户两级。

## 目录

- [功能特性](#功能特性)
- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [核心注解](#核心注解)
- [完整示例](#完整示例)
- [字段提取覆盖（config 表）](#字段提取覆盖config-表)
- [配置说明](#配置说明)
- [高级功能](#高级功能)
- [API 接口](#api-接口)
- [常见问题](#常见问题)

---

## 功能特性

### 核心能力

| 功能 | 说明 |
|------|------|
| **意图识别** | LLM 优先 + 关键词降级的两级识别，返回判断理由（intentReason）便于调试提示词 |
| **多轮参数收集** | 渐进式收集业务参数，缺什么问什么，支持多轮对话 |
| **字段校验** | 内置枚举、非空、正则、长度、数值范围校验 + 自定义校验器 |
| **字段前置条件** | `@AiPremise` 表达式控制字段收集时机（存在性、比较、逻辑组合） |
| **字段标准化** | `normalize` 指定归一化器（SPI 扩展），如手机号、日期统一格式 |
| **任务执行** | 参数收集完成后自动执行 `TaskExecutor`，返回结果 |
| **后置任务** | 任务执行后的钩子（日志、通知、审计等），best-effort 模式 |
| **纯动作场景** | 无需参数收集的场景，只需定义 `@AiTask` 即可 |
| **会话管理** | 会话绑定任务，支持重置、取消、超时静默清理 |
| **多租户隔离** | `(tenantId, sessionId)` 复合隔离，同一 sessionId 在不同租户下相互独立 |
| **状态持久化** | 任务状态数据库持久化，重启可恢复，支持乐观锁并发控制 |
| **对话历史** | 独立 `ai_chat_message` 表存储，滑动窗口注入上下文，支持查询/分页 API |

### 配置热更新

| 功能 | 说明 |
|------|------|
| **注解为主** | 任务结构、字段、校验、前置条件全部由注解声明，启动时构建 |
| **DB 覆盖为辅** | `ai_task_config` 表只覆盖字段提取提示词（description/examples/rules），发布后下次对话即生效 |
| **全局 + 租户两级** | 租户覆盖 > 全局覆盖 > 注解默认 三级合并，租户可独立定制提示词 |

### 工程能力

| 功能 | 说明 |
|------|------|
| **多模型支持** | DeepSeek / Kimi / Doubao / OpenAI 兼容接口，可配置 fallback 链 |
| **限流熔断** | 滑动窗口限流 + 连续失败熔断，保护 LLM 调用 |
| **分布式锁** | 同一 (tenantId, sessionId) 并发请求互斥，防止状态冲突 |
| **雪花算法 ID** | 全局唯一任务 ID |
| **SSE 流式输出** | 支持 Server-Sent Events 流式返回 |
| **上下文变量** | 内置 `currentDate` 等变量注入提取提示词，正确处理"今天/明天/下周"等相对时间 |
| **LLM 调用日志** | 独立开关控制每次调用的完整请求/响应日志 |
| **注解驱动** | 零 XML，声明式配置 |

---

## 架构概览

```
用户消息 (message + sessionId + tenantId)
   │
   ▼
┌───────────────────────┐
│  会话层 SessionManager │  (tenantId, sessionId) 定位会话，分布式锁防并发
└──────────┬────────────┘
           │ 无活跃任务 → 意图识别（LLM 优先 → 关键词降级）
           ▼
┌───────────────────────┐
│  意图识别引擎          │  IntentEngine → taskType + intentReason/confidence
└──────────┬────────────┘
           │ 识别出 taskType
           ▼
┌───────────────────────┐
│  任务引擎 AiTaskEngine │  加载/创建任务状态 → 提取 → 校验 → 标准化 → 追问/执行
└──────────┬────────────┘
           │
      ┌────┴──────────────┐
      ▼                   ▼
┌──────────────┐  ┌──────────────┐
│ 字段提取      │  │ 字段校验/标准化 │  LLM 提取 + 枚举/自定义校验 + 归一化
│ Extraction   │  │Validation/Norm│
└──────┬───────┘  └──────┬───────┘
       │                 │
       └───────┬─────────┘
               │ 全部收集完成（或纯动作场景直接执行）
               ▼
      ┌──────────────┐
      │ Task 执行     │  执行 TaskExecutor 业务逻辑
      └──────┬───────┘
             │
             ▼
      ┌──────────────┐
      │ PostTask     │  日志/通知/审计（best-effort）
      └──────────────┘
```

配置加载（每次对话时合并）：
```
注解配置（内存，启动时构建）  ←  数据库字段覆盖（ai_task_config，每次对话实时查询）
        └───── 三级合并：租户覆盖 > 全局覆盖 > 注解默认 ─────┘
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
  task-engine:
    enabled: true
    annotation:
      enabled: true          # 开启注解扫描（缺省扫描启动类所在包）
      # base-packages: com.your.package

# LLM 配置（provider 指定默认模型，providers 可配置多个用于 fallback）
llm:
  provider: deepseek
  providers:
    deepseek:
      api-key: sk-your-deepseek-key
      endpoint: https://api.deepseek.com        # base URL，不含 /chat/completions
      model: deepseek-chat
    kimi:
      api-key: sk-your-kimi-key
      endpoint: https://api.moonshot.cn/v1
      model: kimi-k2-turbo-preview
    doubao:
      api-key: your-doubao-key
      endpoint: https://ark.cn-beijing.volces.com/api/v3
      model: doubao-seed-1-6-251015
```

### 3. 启动类

框架通过 Spring Boot 自动配置生效，无需额外注解。启动应用后，Liquibase 会自动创建所需表结构。

---

## 核心注解

### @AiTask — 声明任务（标注在执行器类上）

标注在 `TaskExecutor` 实现类上，定义一个 AI 任务的元信息。**每个业务场景有且仅有一个 `@AiTask`**：

```java
@AiTask(
    value = "CREATE_ORDER",
    name = "创建订单",
    description = "通过对话收集订单信息并创建订单",
    triggers = {"创建订单", "下单", "我要买"},
    postActions = {"LOG"}
)
public class CreateOrderTask implements TaskExecutor {

    @Override
    public TaskResult execute(ExecuteContext context) {
        Map<String, Object> params = context.getParameters();
        // 执行业务逻辑...
        return TaskResult.success("订单创建成功！订单号：" + orderNo, result);
    }
}
```

| 属性 | 说明 |
|------|------|
| `value` | 任务唯一标识（唯一权威来源），同时作为 Spring Bean 名称 |
| `name` | 任务名称（用户可见，用于功能介绍） |
| `description` | 任务描述（用户可见，用于功能介绍和意图识别） |
| `triggers` | 触发词，同时作为意图识别的关键词和示例 |
| `postActions` | 后置任务名称列表 |
| `hidden` | 是否在功能介绍中隐藏，默认 false |

> **纯动作场景**：如果任务不需要收集参数（如"查询物流"），只需定义 `@AiTask` 即可，无需 `@AiTaskParam` DTO。

### @AiTaskParam — 声明参数收集（标注在 DTO 类上）

标注在参数 DTO 类上，通过 `type` 与 `@AiTask` 一对一关联：

```java
@Data
@AiTaskParam(type = "CREATE_ORDER")
public class CreateOrderParam {
    // 字段定义见 @AiField / @AiExtract / @AiPremise
}
```

| 属性 | 说明 |
|------|------|
| `type` | 关联的任务类型，必须与某个 `@AiTask.value()` 一致 |

### @AiField — 声明字段元信息

标注在 DTO 字段上，覆盖从 Java 字段推导的默认元信息（字段名、类型、顺序按约定自动推导）：

```java
@AiField(
    name = "订单类型",
    required = true,
    sensitive = false,
    normalize = "PHONE"      // 可选：字段标准化器类型
)
private String orderType;
```

| 属性 | 说明 |
|------|------|
| `name` | 字段名称（用户可见），缺省用 Java 字段名 |
| `required` | 是否必填，默认 false；必填字段不允许为空 |
| `sensitive` | 是否为敏感数据（日志中脱敏） |
| `normalize` | 标准化器类型，与 `FieldNormalizer` Bean 的 `type()` 对应 |

### @AiExtract — 声明提取提示词

标注在 DTO 字段上，声明 LLM 如何从自然语言中提取该字段的值：

```java
@AiExtract(
    description = "客户预约办理业务的日期",
    examples = {"2024年1月1日", "2024/1/1", "2024-01-01"},
    rules = {
        "支持中文日期、斜杠日期、短横线日期等常见日期格式",
        "如果用户使用相对日期（明天、下周一），应根据当前日期计算实际日期"
    },
    contextVars = {"currentDate"}   // 需要注入的上下文变量
)
private String appointmentDate;
```

| 属性 | 说明 |
|------|------|
| `description` | 字段含义描述，发送给 LLM 辅助提取 |
| `examples` | 合法取值示例，发送给 LLM |
| `rules` | 提取规则/约束，发送给 LLM |
| `contextVars` | 需要注入的上下文变量名（由 `ExtractionContextProvider` 提供，如内置 `currentDate`） |

### @AiPremise — 字段前置条件

该字段只有在表达式求值为 `true` 时才参与收集，替代旧版 `@AiDependsOn`：

```java
// 只要 customerName 或 channelName 其中一个存在即可
@AiPremise("customerName != null || channelName != null")

// 组合逻辑
@AiPremise("customerName != null && (phone != null || email != null)")

// 值比较
@AiPremise("ticketType == 'COMPLAINT'")

// 枚举包含
@AiPremise("priority in ('高','中')")
```

**表达式语法**：

| 类型 | 写法 | 示例 |
|------|------|------|
| 存在性 | `!= null` / `== null` | `customerName != null` |
| 比较 | `==` `!=` `>` `<` `>=` `<=` | `amount > 10000` |
| 逻辑与 | `AND` 或 `&&` | `a != null AND b != null` |
| 逻辑或 | `OR` 或 `\|\|` | `a != null OR b != null` |
| 逻辑非 | `!` | `!(a == null)` |
| 包含 | `in (v1, v2)` | `priority in ('高','中')` |
| 分组 | `()` | `a && (b \|\| c)` |

### @AiValid — 字段校验

标注在 DTO 字段上，按类引用校验器（Spring Bean），可重复（多个校验器按声明顺序组成管道）：

```java
@AiField(name = "手机号", required = true)
@AiValid(by = PhoneValidator.class)
private String phone;
```

枚举字段无显式 `@AiValid` 时自动获得内置 `ENUM` 校验（中文标签 → 枚举值转换）。

### @AiMapping — 字段映射

标注在 DTO 字段上，显式声明字段映射目标（默认映射到同名目标 `$value`）：

```java
@AiMapping({
    @Mapping(target = "receiveChannelId", source = "$data.id"),
    @Mapping(target = "receiveChannelName", source = "$value")
})
private String receiveChannelName;
```

### @AiPostTask — 后置任务

标注在 `PostTaskExecutor` 实现类上。类型标识由 `value()` 提供（唯一权威来源），同时作为 Spring Bean 名称：

```java
@AiPostTask("LOG")
public class LogPostTask implements PostTaskExecutor {
    @Override
    public void execute(ExecuteContext context) {
        log.info("操作日志: taskId={}, params={}", context.getTaskId(), context.getParameters());
    }
}
```

后置任务通过主任务的 `@AiTask(postActions = {"LOG"})` 按名称启用，best-effort 执行。

---

## 完整示例

以"创建客服工单"场景为例。

### 1. 定义任务执行器

```java
@AiTask(
    value = "CREATE_TICKET",
    name = "创建工单",
    description = "通过多轮对话收集工单信息并创建工单",
    triggers = {"创建工单", "我要投诉", "我要建议"},
    postActions = {"LOG"}
)
public class CreateTicketTask implements TaskExecutor {

    @Override
    public TaskResult execute(ExecuteContext context) {
        Map<String, Object> params = context.getParameters();
        String ticketNo = "TK" + System.currentTimeMillis();
        // 调用业务 Service 创建工单...
        return TaskResult.success("工单已创建成功！工单编号：" + ticketNo,
                Map.of("ticketNo", ticketNo));
    }
}
```

### 2. 定义参数 DTO

```java
@Data
@AiTaskParam(type = "CREATE_TICKET")
public class CreateTicketParam {

    @AiField(name = "工单类型(咨询/投诉/建议)", required = true)
    private TicketType ticketType;  // 枚举字段自动校验

    @AiField(name = "客户姓名", required = true)
    private String customerName;

    @AiField(name = "联系电话", required = true)
    @AiValid(by = PhoneValidator.class)
    private String phone;

    @AiField(name = "问题描述", required = true)
    @AiPremise("ticketType != null")   // ticketType 收集完成后才追问
    private String description;

    @AiField(name = "优先级", required = false)
    private Priority priority;  // 非必填枚举
}
```

### 3. 定义枚举

```java
public enum TicketType {
    CONSULT("咨询"),
    COMPLAINT("投诉"),
    SUGGESTION("建议");

    private final String label;
    // 构造函数、getter...
}
```

### 4. 自定义校验器

```java
@Component
public class PhoneValidator implements FieldValidator {
    @Override
    public String type() {
        return "PHONE";
    }

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
                .tenantId(req.get("tenantId"))   // String 类型，多租户隔离
                .sessionId(sessionId)
                .data(new HashMap<>())
                .build();

        ChatResponse response = aiChatService.chat(message, sessionId, context);
        return Map.of(
            "message", response.getMessage(),
            "completed", response.isCompleted(),
            "taskId", response.getTaskId(),
            "taskType", response.getTaskType(),
            "intentReason", response.getIntentReason(),          // 意图判断理由
            "intentConfidence", response.getIntentConfidence()   // 意图置信度
        );
    }
}
```

### 6. 对话效果

```
用户: 我要投诉
AI: 好的，请提供工单类型、客户姓名、联系电话等
用户: 我叫张三，电话 13800138000，要投诉物流太慢
AI: 工单已创建成功！工单编号：TK123456789
```

### 7. 纯动作场景示例

无需参数收集的场景，只需定义 Task：

```java
@AiTask(
    value = "LOGISTICS_QUERY",
    name = "物流查询",
    description = "查询订单物流状态和配送进度",
    triggers = {"物流", "查物流", "快递到哪了"}
)
public class LogisticsQueryTask implements TaskExecutor {

    @Override
    public TaskResult execute(ExecuteContext context) {
        // 直接执行，无需参数收集
        return TaskResult.success("当前物流状态：已发货，预计明天送达", null);
    }
}
```

---

## 字段提取覆盖（config 表）

### 设计原则

- **注解为主**：任务结构、字段定义、校验、前置条件全部来自 `@AiTask` / `@AiTaskParam` 注解，启动时构建进内存。
- **DB 覆盖为辅**：`ai_task_config` 表**只负责字段提取提示词**（`description` / `examples` / `rules`）的热更新覆盖，不定义任务结构。
- **发布即生效**：数据库覆盖发布后，下一次对话实时合并，无需重启。

### 三级合并优先级

```
租户覆盖（tenant_id = 'xxx'）  >  全局覆盖（tenant_id IS NULL）  >  注解默认
```

每级都只覆盖"非空的字段项"，未覆盖的项回退下一级。例如租户覆盖只配置了 `phone.rules`，则 `description` 回退到全局/注解值。

### 覆盖数据结构

`config_json` 仅保存覆盖集：

```json
{
  "taskType": "CREATE_TICKET",
  "fields": {
    "phone": {
      "description": "租户联系电话补充",
      "examples": ["13800138000"],
      "rules": ["必须是11位大陆手机号"]
    }
  }
}
```

字段 code 必须与 `@AiTaskParam` DTO 中声明的字段一致，保存时会校验，引用不存在的字段返回 400。

### Config 管理 API

```bash
# 1. 保存草稿（tenantId 省略/null = 全局模板；version 省略自动分配）
POST /easyai/engine/config/save
{"taskType":"CREATE_TICKET","tenantId":"T-ISO",
 "fields":{"phone":{"rules":["租户规则：ISO手机号校验"]}}}

# 2. 发布草稿（发布后新任务立即生效，同作用域旧发布自动禁用）
POST /easyai/engine/config/publish
{"taskType":"CREATE_TICKET","tenantId":"T-ISO","version":1}

# 3. 查询最新已发布配置（返回注解 + DB 覆盖合并后的完整配置）
GET  /easyai/engine/config/latest?taskType=CREATE_TICKET&tenantId=T-ISO

# 4. 列出配置记录（按版本降序）
GET  /easyai/engine/config/list?taskType=CREATE_TICKET&tenantId=T-ISO

# 5. 禁用已发布配置（存量任务保持绑定版本不变）
POST /easyai/engine/config/disable
{"taskType":"CREATE_TICKET","tenantId":"T-ISO","version":1}
```

---

## 配置说明

### LLM 配置

```yaml
llm:
  provider: deepseek  # 默认激活的模型
  providers:
    deepseek:
      api-key: sk-xxx
      endpoint: https://api.deepseek.com  # base URL，不含 /chat/completions
      model: deepseek-chat
```

> 内置支持 `kimi` / `deepseek` / `doubao` / `openai_compatible` 四种 provider，
> 也支持通过完整类名或 Java SPI 接入自定义 LLM 实现。

### 框架配置

```yaml
easy-ai:
  task-engine:
    enabled: true
    annotation:
      enabled: true
      # base-packages: com.your.package   # 缺省扫描启动类所在包
    llm:
      max-retries: 3                        # LLM 调用最大重试次数
      initial-backoff-ms: 1000              # 初始退避时间
      backoff-multiplier: 2.0               # 退避倍数
      fallback-models:                      # fallback 模型链
        - kimi
        - doubao
      max-input-length: 2000                # 用户消息最大长度（字符）
      injection-detection-enabled: true     # 提示词注入检测
      log-enabled: false                    # 每次 LLM 调用完整请求/响应日志（调试用，生产关闭）
    lifecycle:
      max-turns: 10                         # 单任务最大对话轮数，超出标记 FAILED
      timeout-minutes: 30                   # 任务超时（分钟），超出标记 EXPIRED
      recovery-guidance-enabled: true       # 恢复未完成任务时的引导
      expire-enabled: true                  # 后台定时任务清理过期任务
      expire-interval-ms: 600000            # 定时清理间隔（毫秒）
    snowflake:
      worker-id: 0                          # 多实例部署时每实例唯一
      datacenter-id: 0
    resilience:
      enabled: true
      rate-limit-per-second: 10             # 限流 QPS
      rate-limit-window-seconds: 1
      circuit-breaker-sliding-window-size: 20
      circuit-breaker-failure-rate-threshold: 50.0
      circuit-breaker-wait-duration-in-open-state-seconds: 30
      circuit-breaker-permitted-number-of-calls-in-half-open-state: 5
      circuit-breaker-minimum-number-of-calls: 10
```

### 数据库表

框架启动时通过 Liquibase 自动创建以下表：

| 表名 | 说明 |
|------|------|
| `ai_chat_session` | 会话表，`(tenant_id, session_id)` 复合唯一，多租户隔离 |
| `ai_chat_session_task` | 任务状态表，存储完整 TaskState JSON，乐观锁并发控制 |
| `ai_chat_message` | 对话历史表，独立存储每轮消息，滑动窗口注入上下文 |
| `ai_task_config` | 字段提取覆盖表，`(tenant_id, task_type, version)` 唯一，仅存覆盖集 JSON |
| `ai_task_lock` | 分布式锁表 |

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

### 字段标准化（Normalization）

实现 `FieldNormalizer` 并注册为 Spring Bean，通过 `@AiField(normalize = "xxx")` 使用：

```java
@Component
public class PhoneNormalizer implements FieldNormalizer {
    @Override
    public String type() {
        return "PHONE";
    }

    @Override
    public NormalizationResult normalize(Object value, FieldContext context) {
        // 138-0013-8000 / 138 0013 8000 → 13800138000
        String normalized = String.valueOf(value).replaceAll("[^0-9]", "");
        return NormalizationResult.success(normalized);
    }
}
```

标准化在**校验之后、映射之前**执行，每个字段只执行一次。

### 上下文变量（相对日期）

框架内置 `CurrentDateContextProvider`，通过 `@AiExtract(contextVars = {...})` 声明使用，帮助 LLM 理解"今天/明天/下周"等相对日期：

| 变量 | 含义 | 示例 |
|------|------|------|
| `currentDate` | 当前日期 | `2026-08-29` |
| `currentWeekday` | 当前星期 | `星期六` |
| `currentMonth` | 当前月份 | `八月` |

```java
@AiExtract(
    description = "预约日期",
    rules = {"如果用户使用相对日期，应根据当前日期计算实际日期"},
    contextVars = {"currentDate"}
)
private String appointmentDate;
```

### 意图识别原因

每次意图识别都返回判断依据，方便调试提示词和排查误识别：

```java
ChatResponse resp = aiChatService.chat(message, sessionId, context);
resp.getIntentReason();      // 如："用户明确表达'创建工单'，与CREATE_TICKET意图直接匹配"
resp.getIntentConfidence();  // 0.0-1.0
resp.getIntentSource();      // LLM / KEYWORD / FALLBACK / CONTINUE
```

意图原因也会持久化到任务状态（`ai_chat_session_task`），供事后分析。

### 指定任务类型

跳过意图识别，直接进入指定任务：

```java
ChatResponse response = aiChatService.chatWithTaskType(
    message, sessionId, "CREATE_TICKET", context);
```

### 会话管理与多租户隔离

`tenantId` 为 **String 类型**，`(tenantId, sessionId)` 复合键定位会话。同一 sessionId 在不同租户下是相互独立的会话，不会串数据。会话超过 `timeout-minutes` 未活跃自动静默重置并开启新会话（无打扰提示）。

```java
// 查看会话状态
AiChatSession session = sessionManager.loadOrCreate(sessionId, tenantId);

// 重置会话
sessionManager.reset(sessionId, tenantId);

// 清除任务绑定
sessionManager.clearTask(sessionId, tenantId);
```

### 对话历史查询

独立 `ai_chat_message` 表存储历史消息，支持查询/分页/计数/清空（均按租户隔离）：

```bash
GET    /easyai/engine/history/{sessionId}?tenantId=001          # 全部消息（时间升序）
GET    /easyai/engine/history/{sessionId}/page?page=1&size=20&tenantId=001
GET    /easyai/engine/history/{sessionId}/count?tenantId=001    # 消息总数
DELETE /easyai/engine/history/{sessionId}?tenantId=001          # 清空（逻辑删除）
```

### 任务状态查询

```java
TaskState state = taskStateManager.load(taskId, "CREATE_TICKET", 1);
// state.getFields() → 各字段收集状态（含 extractReason 提取依据）
// state.getStatus() → INITIALIZED / COLLECTING / COMPLETED / FAILED / EXPIRED
```

### SSE 流式输出

框架内置 SSE 接口：

```
POST /easyai/engine/chat/stream
Content-Type: application/json

{
    "sessionId": "xxx",
    "message": "我要投诉",
    "tenantId": "001"
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

### 内置校验器

| type | 说明 |
|------|------|
| `ENUM` | 枚举标签 → 值转换（枚举字段自动启用） |
| `NOT_EMPTY` | 非空校验 |
| `CONFIRM` | 确认类字段（如"请回复确认"） |
| `REGEX` | 正则校验 |
| `STRING_LENGTH` | 字符串长度校验 |
| `NUMBER_RANGE` | 数值范围校验 |

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
| `taskResult` | 任务执行结果（完成时） |
| `taskState` | 当前任务状态 |
| `intentReason` | 意图判断理由（调试提示词用） |
| `intentConfidence` | 意图置信度 0.0-1.0 |
| `intentSource` | 意图匹配来源（LLM / KEYWORD / FALLBACK / CONTINUE） |

### REST 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/easyai/engine/chat/auto` | 自动意图识别对话 |
| POST | `/easyai/engine/chat` | 指定任务类型对话 |
| POST | `/easyai/engine/chat/stream` | SSE 流式对话 |
| GET | `/easyai/engine/history/{sessionId}` | 查询对话历史 |
| GET | `/easyai/engine/history/{sessionId}/page` | 分页查询历史 |
| GET | `/easyai/engine/history/{sessionId}/count` | 历史消息总数 |
| DELETE | `/easyai/engine/history/{sessionId}` | 清空历史 |
| POST | `/easyai/engine/config/save` | 保存字段提取覆盖草稿 |
| POST | `/easyai/engine/config/publish` | 发布覆盖 |
| POST | `/easyai/engine/config/disable` | 禁用覆盖 |
| GET | `/easyai/engine/config/list` | 列出覆盖记录 |
| GET | `/easyai/engine/config/latest` | 查询合并后的最新配置 |

### 内置功能（FEATURE_INTRO）

框架内置 `FEATURE_INTRO` 任务，当用户问"你能做什么"、"有什么功能"时自动触发。**动态扫描**所有已注册的 `@AiTask`（非 hidden），实时生成功能清单，新增业务任务时无需修改任何代码。

---

## 常见问题

### Q: 意图识别不准确怎么办？

A: 在 `@AiTask` 的 `triggers` 中补充更多用户表达方式。LLM 分类优先，关键词作为降级兜底。同时关注响应中的 `intentReason`，判断 LLM 是否基于正确理由命中意图。

### Q: 如何调试 LLM 调用？

A: 开启 LLM 调用日志：
```yaml
easy-ai:
  task-engine:
    llm:
      log-enabled: true
```
会输出每次调用的完整请求（system + user）与响应。生产环境建议关闭。

### Q: 字段提取提示词想改但不想改代码怎么办？

A: 使用 config 表覆盖。`POST /easyai/engine/config/save` 只配置目标字段的 `description/examples/rules`，发布后立即生效。租户级配置可针对不同租户定制提示词。

### Q: 支持哪些数据库？

A: 目前支持 MySQL，通过 Liquibase 自动管理表结构。

### Q: 如何添加新的 LLM 模型？

A: 实现 `LLMProvider` 接口，或直接使用 `openai_compatible` 通用 Provider（只要提供 OpenAI 兼容的 `/chat/completions` 接口）。

### Q: 纯动作场景（不需要参数）怎么定义？

A: 只需标注 `@AiTask` 并实现 `TaskExecutor` 即可，不需要 `@AiTaskParam` DTO。框架识别到任务后会直接执行，跳过参数收集阶段。

### Q: 多租户场景如何使用？

A: 每个请求在 `TaskContext.tenantId` 传入租户标识（String 类型）。会话、对话历史、任务状态、config 覆盖均按 `(tenantId, sessionId)` / `(tenant_id, ...)` 隔离，互不串扰。

---

## License

MIT License
