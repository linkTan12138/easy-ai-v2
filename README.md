# 🚀 EasyAI Spring Boot Starter 使用文档
**```easyAi```** 是一款基于 Java + Spring Boot 的智能客服框架，能够快速集成到任意 Spring Boot 项目中。

它专注于企业客服自动化场景，通过**图形化配置** + **动态场景解析** + **智能参数收集**的方式，让开发者快速轻松构建一个具备上下文理解能力的智能客服系统。

框架内置灵活的**场景编排**机制，支持**识别用户意图**、**匹配目标功能**、**逐步收集所需参数**，并自动驱动后端业务逻辑，使 AI 作为系统中的一个“智能入口”。

## ✨ **核心特点**

#### 🔧 **1. 图形化场景配置**

通过可视化界面即可配置：

**场景名称**

**场景描述**

匹配关键词 / 意图识别

无需改代码即可扩展新的客服流程。
![](https://easyblog-1310944511.cos.ap-guangzhou.myqcloud.com/uploads/03f22a3d-1ab7-4275-b82e-3e2b2611b913.png)

#### 🧠 2. **动态参数收集**

当用户提出一个需求时（如 “**帮我修改订单**”），框架会自动识别内容意图并匹配到对应场景。

并根据该场景的参数配置进入“**参数收集模式**”。

未提供的参数会通过自然语言与用户交互完成补全。
![](https://easyblog-1310944511.cos.ap-guangzhou.myqcloud.com/uploads/2321006f-e8f8-414c-92df-656766a3c254.png)
![](https://easyblog-1310944511.cos.ap-guangzhou.myqcloud.com/uploads/34475ec1-3c24-433c-bc2c-67089e2abc44.png)

---
**字段讲解**：
* ``字段``
  > 收集字段名，对应javaBean的字段名
  >
  > 收集的内容会以这个字段名存储值
* ``名称``
  > 字段中文名，ai对话时提示提供字段值时以这个名称作为提问内容

* ``字段类型``

  > 收集字段的类型：String,Integer,Long,String[]

* ``前置字段``

  > 当前字段提示的前提必须完成收集的字段

* ``判断逻辑``

  > ai大模型通过这段内容作为提示对用户会话内容来进行判断匹配当前会话值属于哪个字段

* ``示例``

  > 字段值的示例

* ``枚举值``

  > 字段值转换对应的枚举值。
  > 例如：确认->1，女->0

* ``敏感``

  > 勾选当前字段为敏感字段，敏感字段会在会话记录中以***替换，以免暴露重要信息

* ``必填``

  > 只有必填字段全部完成收集，收集任务才会结束

 ---


#### 🔌 3. 一行依赖即可接入

项目采用 Spring Boot Starter 的方式提供，自动装配 Mapper、Service、Controller，不需要复杂的配置，开箱即用。



## 效果展示

![](https://easyblog-1310944511.cos.ap-guangzhou.myqcloud.com/uploads/46f1e15a-32f6-4be1-93f9-c8c38015d14f.png)
![](https://easyblog-1310944511.cos.ap-guangzhou.myqcloud.com/uploads/42fff72d-5529-4965-8a14-55a298da07d8.png)

### 说明
> 上图演示的效果包含：根据不同的会话匹配到对应的场景中。
>
> 图1：匹配到功能列表功能
>
>
> 图2：匹配到修改订单功能
>> 其中，修改订单场景展示了收集参数，isConfirm提示的前提，isConfirm字段的枚举映射。


# 快速开始

## 1、 引入依赖
```xml
        <dependency>
            <groupId>com.link</groupId>
            <artifactId>easyAi</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
```

## 2、配置application.yml
> 添加ai大模型配置，目前仅支持``deepseek``,``doubao``,``kimi``
>
> **active**:指定使用模型
```yml
large-language-model:
  active: kimi
  deepseek:
    api:
      key: sk-xxxxxxxxxxxxx
      url: https://api.deepseek.com/chat/completions
      model: deepseek-chat

  doubao:
    api:
      key: xxxxxxxxxxxxxxxxxxx
      url: https://ark.cn-beijing.volces.com/api/v3/chat/completions
      model: doubao-seed-1-6-251015

  kimi:
    api:
      key: sk-xxxxxxxxxxxxxxxx
      url: https://api.moonshot.cn/v1/chat/completions
      model: kimi-k2-turbo-preview
```

## 3、执行建表sql
``easyai``需要使用到**数据库**。

您的项目必须连接数据库，提前配置好数据源。
### 模板表
```sql
CREATE TABLE `tb_task_field_template` (
  `id` bigint NOT NULL,
  `template_name` varchar(255) NOT NULL DEFAULT '' COMMENT '模板名称',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '' COMMENT '模板备注',
  `field_list` json DEFAULT NULL COMMENT '字段列表',
  `scenario_code` int NOT NULL DEFAULT '0' COMMENT '场景: 0：无场景匹配 1：注册账号 2:写博客',
  `create_by` bigint NOT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT 'label=更新人',
  `create_time` datetime DEFAULT NULL COMMENT 'label=创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'label=更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'label=是否删除;bizrule=1:是/0:否',
  `tenant_id` bigint NOT NULL COMMENT '租户id',
  `enable` int NOT NULL DEFAULT '0' COMMENT '是否启用 1：是 0：否',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
```

### 会话任务表
```sql
CREATE TABLE `tb_chat_session_task` (
  `id` bigint NOT NULL,
  `type` int NOT NULL DEFAULT '0' COMMENT '任务类型；bizrule：0:参数收集',
  `scenario_code` int NOT NULL DEFAULT '0' COMMENT '场景: 0：无场景匹配 1：注册账号 2:写博客',
  `field_list` json DEFAULT NULL COMMENT '字段列表',
  `records` json DEFAULT NULL COMMENT '历史会话记录',
  `extra_content` json DEFAULT NULL COMMENT '扩展字段，用于存储对话过程中产生的字段',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态: 0-待处理 1-待唤醒 2-处理中 3-失败 4-已停止 5-已完成 ',
  `create_by` bigint NOT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT 'label=更新人',
  `create_time` datetime DEFAULT NULL COMMENT 'label=创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'label=更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'label=是否删除;bizrule=1:是/0:否',
  `tenant_id` bigint NOT NULL COMMENT '租户id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='desc=会话任务';
```

### 会话记录表
```sql
CREATE TABLE `tb_chat_record` (
  `id` bigint NOT NULL,
  `record` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '????',
  `type` int NOT NULL DEFAULT '0' COMMENT '会话方向;0：客户 1：ai',
  `create_by` bigint NOT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT 'label=更新人',
  `create_time` datetime DEFAULT NULL COMMENT 'label=创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'label=更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'label=是否删除;bizrule=1:是/0:否',
  `tenant_id` bigint NOT NULL COMMENT '租户id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

## 4、自定义场景

### 4.1、配置场景
![](https://easyblog-1310944511.cos.ap-guangzhou.myqcloud.com/uploads/97763f0b-5073-4ff5-8111-20d65d4cd2b4.png)

### 4.2、编写场景实现类
场景实现类需要继承AbstractAiSceneProcessor类

并添加 ***@AiScene(4)*** 注解，其中value为上图**场景编码**

并实现 ***transScenarioMsg***、***doProcess***、***validField*** 三个方法。

1、 ***transScenarioMsg***：
自定义场景名称

2、 ***doProcess***：

```java

@AiScene(4)
public class AiSceneProcessorForUpdateOrder extends AbstractAiSceneProcessor {


    @Override
    public String transScenarioMsg() {
        return "修改订单";
    }

    @Override
    public AiChatResponseVo doProcess(AiSceneProcessorDto processorDto) {
        CollectParamDto collectParamDto = CollectParamDto.build(processorDto.getTaskId()).message(processorDto.getMessage());
        CollectParamVo<UpdateOrderDto> collectParamVo = collectParam(collectParamDto, UpdateOrderDto.class);
        if(collectParamVo.isSuccess()) {
            UpdateOrderDto updateOrderDto = collectParamVo.getT();
            // TODO 调用api进行订单的修改
            return AiChatResponseVo.build().message("修改成功！\n信息:%s".formatted(JSONUtil.toJsonStr(updateOrderDto))).status(TaskStatusEnum.COMPLETED);
        }

        return AiChatResponseVo.build().message(collectParamVo.getMessage()).status(TaskStatusEnum.PENDING);
    }

    @Override
    public CollectFieldValidVo validField(String field, Object value) {
        switch (field) {
            case "isConfirm":
                if(value != Integer.valueOf(1)) {
                    return CollectFieldValidVo.build(false).errMsg("请回复“确认”以修改订单。");
                }
                return CollectFieldValidVo.build(true);
        }
        return super.validField(field, value);
    }
}

```

使用 ***collectParam*** 方法进行参数的收集，

当所有**必填**参数收集完成，***collectParamVo.isSuccess*** 为 ***true***
```java
@Data
public class UpdateOrderDto {

    private String customerNo;
    private String countryName;
    private Integer pieceCount;
    private Integer isConfirm;
}


CollectParamDto collectParamDto = CollectParamDto.build(processorDto.getTaskId())
                                                 .message(processorDto.getMessage());
CollectParamVo<UpdateOrderDto> collectParamVo = collectParam(collectParamDto, UpdateOrderDto.class);
```
3、 ***validField***：

对收集到的字段值进行**校验**

```java
@Override
public CollectFieldValidVo validField(String field, Object value) {
    switch (field) {
        case "countryName":
            Country country = coutryService.findByName(String.valueOf(value));
            if(country == null) {
                return CollectFieldValidVo.build(false).errMsg("不存在的国家：%s，请重新提供“国家”".formatted(value));
            }
        case "isConfirm":
            if(value != Integer.valueOf(1)) {
                return CollectFieldValidVo.build(false).errMsg("请回复“确认”以修改订单。");
            }
            return CollectFieldValidVo.build(true);
    }
    return super.validField(field, value);
}
```

## 5、权限控制

``easyai``通过当前登录人信息调用所有api接口，以达到**权限控制**的目的

用户类需要实现**UserDetails**类，并实现***getTenantId***、***getUsername***、***getId*** 方法

实现```UserDetailsService```接口，并实现 **getUser** 方法，返回当前登录人信息

```java
public class CustomerUser implements UserDetails {
    private Long id;
    private String username;
    private String nickName;
    private String avatar;
    private String password;
    private Long tenantId;

    @Override
    public Long getTenantId() {
        return this.tenantId;
    }

    @Override
    public String getUsername() {
        return this.nickName;
    }

    @Override
    public Long getId() {
        return this.getId();
    }
}


@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails getUser() {
        CustomerUser customerUser = user = (CustomerUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return customerUser;
    }
}


```


## 6、一切准备就绪
使用AiService的chat方法进行交互
```java
@Autowired
private AiService aiService;

AiChatResponseVo response = aiService.chat(message);
```
快去尝试创建一个属于你项目的专属ai助手吧！！