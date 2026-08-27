# 客户自定义 LLM Provider 接入示例

## 项目结构

```
customer-llm-example/
├── src/main/java/com/customer/llm/
│   └── MyCustomLLMProvider.java          # 自定义 Provider 实现
└── src/main/resources/META-INF/services/
    └── com.link.easyai.starter.llm.LLMProvider  # SPI 注册文件
```

## 接入方式

### 方式一：完整类名配置（最简单）

1. 将 `MyCustomLLMProvider.java` 编译为 class 或 jar
2. 放入主应用的 classpath
3. 在 application.yml 中配置：

```yaml
llm:
  provider: com.customer.llm.MyCustomLLMProvider
  api-key: ${CUSTOM_LLM_KEY}
  endpoint: https://api.mycompany.com
  model: my-model-v1
```

### 方式二：SPI 自动注册

1. 在 jar 中包含 `META-INF/services/com.link.easyai.starter.llm.LLMProvider` 文件
2. 文件内容为实现类的完整类名（每行一个）
3. 将 jar 引入主应用
4. 配置简化为：

```yaml
llm:
  provider: my_custom_llm   # 对应 getName() 的返回值
  api-key: ${CUSTOM_LLM_KEY}
  endpoint: https://api.mycompany.com
  model: my-model-v1
```

## 实现规范

1. **实现接口**：`implements LLMProvider`
2. **构造函数**：必须有 `public XxxProvider(LLMConfig.ProviderConfig config)`
3. **三个方法**：
   - `chat(List<Message>, LLMConfig)`：同步对话
   - `streamChat(List<Message>, LLMConfig)`：流式对话（不支持可抛异常）
   - `getName()`：返回唯一短名，用于 SPI 注册和配置查找
4. **自定义参数**：通过 `config.getExtra().get("xxx")` 获取配置中的额外字段

## 多 Provider 配置（Fallback 场景）

```yaml
llm:
  provider: my_custom_llm
  providers:
    my_custom_llm:
      api-key: key1
      endpoint: https://api.mycompany.com
      model: model-v1
    deepseek:
      api-key: key2
      endpoint: https://api.deepseek.com
      model: deepseek-chat
```
