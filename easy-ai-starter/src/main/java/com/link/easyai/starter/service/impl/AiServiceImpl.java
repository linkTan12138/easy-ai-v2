package com.link.easyai.starter.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.link.easyai.starter.config.AiSceneHolder;
import com.link.easyai.starter.config.LargeLanguageModelHolder;
import com.link.easyai.starter.domain.dto.AiSceneProcessorDto;
import com.link.easyai.starter.domain.dto.CollectParamFieldDto;
import com.link.easyai.starter.domain.dto.PageForm;
import com.link.easyai.starter.domain.entity.TbChatRecord;
import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import com.link.easyai.starter.domain.entity.TbTaskFieldTemplate;
import com.link.easyai.starter.domain.enums.ScenarioCodeEnum;
import com.link.easyai.starter.domain.enums.TaskStatusEnum;
import com.link.easyai.starter.domain.vo.AiChatResponseVo;
import com.link.easyai.starter.domain.vo.TaskRecordVo;
import com.link.easyai.starter.domain.vo.TbChatSessionTaskVo;
import com.link.easyai.starter.mapper.TbChatRecordMapper;
import com.link.easyai.starter.mapper.TbChatSessionTaskMapper;
import com.link.easyai.starter.service.AiSceneProcessor;
import com.link.easyai.starter.service.AiService;
import com.link.easyai.starter.service.LargeLanguageModel;
import com.link.easyai.starter.service.TbTaskFieldTemplateService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiServiceImpl implements AiService {

    private final LargeLanguageModel largeLanguageModel;
    @Autowired
    public AiServiceImpl(LargeLanguageModelHolder largeLanguageModelHolder) {
        this.largeLanguageModel = largeLanguageModelHolder.getLargeLanguageModel();;
    }

    @Autowired
    private TbChatSessionTaskMapper tbChatSessionTaskMapper;
    @Autowired
    private TbTaskFieldTemplateService tbTaskFieldTemplateService;
    @Autowired
    private TbChatRecordMapper tbChatRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatResponseVo chat(String message) {
        Date startDate = new Date();
        tbChatRecordMapper.insert(TbChatRecord.builder(message,0));

        TbChatSessionTask task = mergeTask(message);
        updateTaskStatus(task.getId(), TaskStatusEnum.PROCESSING.getCode());

        AiSceneProcessor processor = AiSceneHolder.getSceneBySceneCode(task.getScenarioCode());

        AiChatResponseVo aiChatResponse = null;
        try {
            aiChatResponse = processor.process(AiSceneProcessorDto.build().setTaskId(task.getId()).setMessage(message).setLargeLanguageModel(largeLanguageModel));
        } catch (Exception e) {
            String errMsg = transException(e);
            aiChatResponse = AiChatResponseVo.build().message(errMsg).status(TaskStatusEnum.FAILED);
        }
        updateTaskStatus(task.getId(), aiChatResponse.getStatus().getCode());
        String resultMsg = updateTaskRecord(task.getId(), message, aiChatResponse.getMessage(), startDate);
        aiChatResponse.taskId(task.getId()).message(resultMsg);
        tbChatRecordMapper.insert(TbChatRecord.builder(resultMsg,1));
        return aiChatResponse;
    }

    @Override
    public void updateTaskStatus(Long id, Integer status) {
        if(status == null) return;
        tbChatSessionTaskMapper.update(null,new LambdaUpdateWrapper<TbChatSessionTask>()
                .eq(TbChatSessionTask::getId, id)
                .set(TbChatSessionTask::getStatus, status));
    }

    private String transException(Exception e) {
        String msg = largeLanguageModel.chatCompletion("整理一下报错信息，翻译成不是开发人员也能理解的简洁语言，只输出错误信息", e.getMessage());
        return msg;
    }


    private String updateTaskRecord(Long taskId, String message, String result, Date startDate) {

        TbChatSessionTask task = tbChatSessionTaskMapper.selectById(taskId);
        String records = task.getRecords();
        List<TaskRecordVo> recordVoList = new ArrayList<>();
        if(StringUtils.isNotBlank(records)) {
            recordVoList = JSONUtil.parseArray(records).toList(TaskRecordVo.class);
        }
        recordVoList.add(new TaskRecordVo("link", message, startDate,0));
        recordVoList.add(new TaskRecordVo("ai智能小助手", result, DateUtil.date(),1));

        // 去敏感信息
        result = sensitiveHandle(recordVoList,result,task);
        tbChatSessionTaskMapper.update(null,new LambdaUpdateWrapper<TbChatSessionTask>()
                .eq(TbChatSessionTask::getId, task.getId())
                .set(TbChatSessionTask::getRecords, task.getRecords())
                .set(TbChatSessionTask::getExtraContent, task.getExtraContent()));
        return result;
    }

    /**
     * 去敏感信息
     *
     * @param recordVoList
     * @param result
     * @param task
     * @return
     */
    private String sensitiveHandle(List<TaskRecordVo> recordVoList, String result, TbChatSessionTask task) {
        String updateRecord = JSONUtil.toJsonStr(recordVoList);
        String extraContent = task.getExtraContent();

        if(StringUtils.isBlank(updateRecord)) return result;

        if(!"{}".equals(extraContent)) {
            String fieldListStr = task.getFieldList();
            List<CollectParamFieldDto> fieldList = JSONUtil.parseArray(fieldListStr).toList(CollectParamFieldDto.class);
            List<String> sensitiveFields = fieldList.stream()
                    .filter(field -> field.getSensitive() != null && field.getSensitive().equals(1))
                    .map(field -> field.getField()).toList();

            List<String> sensitiveValues = new ArrayList<>();
            JSONObject extraObj = JSONUtil.parseObj(extraContent);
            for (String sensitiveField : sensitiveFields) {
                if(!extraObj.containsKey(sensitiveField)) continue;
                String value = extraObj.get(sensitiveField).toString();
                sensitiveValues.add(value);
                if(Arrays.asList(TaskStatusEnum.COMPLETED.getCode(), TaskStatusEnum.FAILED.getCode(),TaskStatusEnum.STOPPED.getCode()).contains(task.getStatus())) extraObj.put(sensitiveField, "***");
            }

            if(!CollectionUtils.isEmpty(sensitiveValues)) {
                for (String sensitiveValue : sensitiveValues) {
                    updateRecord = updateRecord.replaceAll(sensitiveValue, "***");
                    result = result.replaceAll(sensitiveValue, "***");
                }
                extraContent = JSONUtil.toJsonStr(extraObj);
                task.setExtraContent(extraContent);
            }
        }

        task.setRecords(updateRecord);

        return result;
    }


    private final String prompt = """
            你是一名对话语义匹配专家。
           
            你的任务：
            基于"当前会话文本"与"历史任务列表"进行语义匹配，判断当前会话文本属于哪个任务。
           
            🎯 一、输入结构说明
           
            你将收到以下两部分输入：
           
            历史任务列表（historyTasks）
            结构如下：
            
            {
               "任务ID1": "历史会话记录的JSON字符串",
               "任务ID2": "历史会话记录的JSON字符串",
               ...
            }
            
            
            每个 value 是一个完整的会话数组，
            其中：
                type:0代表是用户的对话内容，type:1代表是ai的回复内容。
                time:指对话时间
                message:指对话内容
                nickname:type:0指用户的昵称，type:1指ai客服的昵称
            例如：
            
            [
               { "time": 123, "type": 0, "message": "用户说的话", "nickname": "xxx" },
               { "time": 124, "type": 1, "message": "AI回的话", "nickname": "xxx" },
               ...
            ]
            
            
            当前会话文本（currentMessage）
            用户最新说的一句话，例如：
            
            @Abc123456
            我修改一下昵称
            验证码是 882188
            
            二、匹配原则（非常重要，必须严格遵守）
            规则 1：必须使用整个“历史会话数组”作为语义上下文
            
            历史任务中的所有 message 共同构成一个完整场景语义，例如“注册账号流程”。
            
            你必须基于整段对话意图和流程来判断，而不是单条 message。
            
            规则 2：当前消息允许是字段值（低语义）
            
            如果历史任务的上下文显示正在收集某个字段（如密码、验证码、邮箱等），
            则当前 message 即使语义弱（如“123456”、“@abc123”），也允许判断为匹配。
            
            规则 3：任务整体语义相似度 ≥ 0.7 则视为匹配
            
            比较方式包括但不限于：
            
            整体场景是否一致（如“注册流程”、“找回密码流程”、“下单流程”）
            
            所需字段是否一致（如历史任务正在等待密码 → 当前输入很像密码）
            
            当前消息是否自然延续任务的对话阶段
            
            规则 4：如果多个任务都可能匹配，只选相似度最高的一个
            规则 5：如果所有任务都与当前会话关联性弱（< 0.7），返回空字符串 ""
            
            三、你必须进行的步骤
            
            对于每一个历史任务（每个 key）：
            
            解析 JSON 数组，理解完整对话内容
            
            推断该历史任务的对话意图和流程阶段
            例如：
            
            这是注册任务吗？
            
            已经收集哪些字段？
            
            当前缺少什么字段？
            
            将“历史任务上下文”与“当前消息”进行语义关联判断
            
            计算语义相似度（模型自行判断理解）
            
            最终选择最高相似度的任务 key
            
            四、最终输出要求（必须遵守）
            
            仅返回最匹配任务的 key
            
            如果没匹配上，返回空null
            
            不要返回解释，不要附带任何其他内容
           """;
    /**
     * 合并任务
     *
     * @param message
     * @return
     */
    private TbChatSessionTask mergeTask(String message) {
        // 未完结任务
        List<TbChatSessionTask> historyTasks = tbChatSessionTaskMapper.selectList(new LambdaQueryWrapper<TbChatSessionTask>()
                .ne(TbChatSessionTask::getScenarioCode, ScenarioCodeEnum.DEFAULT.getCode())
                .eq(TbChatSessionTask::getStatus, TaskStatusEnum.PENDING.getCode())
                .isNotNull(TbChatSessionTask::getRecords));
        if(CollectionUtils.isEmpty(historyTasks)) {
            TbChatSessionTask task = createChatSessionTask(message);
            return task;
        }
        // 场景匹配
        Integer scenarioCode = matchScenario(message);
        if(scenarioCode != ScenarioCodeEnum.DEFAULT.getCode()) {
            TbChatSessionTask task = tbChatSessionTaskMapper.selectOne(new LambdaQueryWrapper<TbChatSessionTask>()
                    .eq(TbChatSessionTask::getScenarioCode, scenarioCode)
                    .eq(TbChatSessionTask::getStatus, TaskStatusEnum.PENDING.getCode())
                    .last("limit 1"));
            if(task == null) task = createChatSessionTask(scenarioCode);
            return task;
        }

        String transMessage = """
                历史任务列表：
                    %s
                
                当前会话文本：
                    %s
                """;

        Map<Long, String> recordMap = historyTasks.stream().collect(Collectors.toMap(TbChatSessionTask::getId, TbChatSessionTask::getRecords));
        String res = largeLanguageModel.chatCompletion(prompt,transMessage.formatted(JSONUtil.toJsonStr(recordMap),message));
        try{
            Long taskId = Long.parseLong(res);
            // 场景匹配
            TbChatSessionTask task = tbChatSessionTaskMapper.selectById(taskId);
            if(scenarioCode == ScenarioCodeEnum.TERMINAL_TASK.getCode()) {
                updateTaskStatus(task.getId(), TaskStatusEnum.STOPPED.getCode());
                task.setScenarioCode(scenarioCode);
                return task;
            }
            if(task != null) return task;

            return createChatSessionTask(scenarioCode);
        } catch (Exception ignore) {
            // 场景匹配
            TbChatSessionTask task = createChatSessionTask(message);
            return task;
        }

    }

    private TbChatSessionTask createChatSessionTask(String message) {
        // 场景匹配
        Integer scenarioCode = matchScenario(message);
        if(scenarioCode == ScenarioCodeEnum.DEFAULT.getCode()) {
            TbChatSessionTask task = tbChatSessionTaskMapper.selectOne(new LambdaQueryWrapper<TbChatSessionTask>()
                    .eq(TbChatSessionTask::getScenarioCode, ScenarioCodeEnum.DEFAULT.getCode())
                    .last("limit 1"));
            if(task != null) return task;
        }
        return createChatSessionTask(scenarioCode);
    }

    private TbChatSessionTask createChatSessionTask(Integer scenarioCode) {
        if(scenarioCode != ScenarioCodeEnum.DEFAULT.getCode()) terminalPreTask();

        TbChatSessionTask task = new TbChatSessionTask();
        task.setType(0);
        TbTaskFieldTemplate tbTaskFieldTemplate = tbTaskFieldTemplateService.findByScenarioCode(scenarioCode);
        if (tbTaskFieldTemplate != null) {
            task.setFieldList(tbTaskFieldTemplate.getFieldList());
        }
        task.setScenarioCode(scenarioCode);
        task.setExtraContent("{}");
        tbChatSessionTaskMapper.insert(task);
        return task;
    }

    private void terminalPreTask() {
        TbChatSessionTask lastTask = tbChatSessionTaskMapper.selectOne(new LambdaQueryWrapper<TbChatSessionTask>()
                .ne(TbChatSessionTask::getScenarioCode, ScenarioCodeEnum.DEFAULT.getCode())
                .notIn(TbChatSessionTask::getStatus, Arrays.asList(TaskStatusEnum.FAILED.getCode(), TaskStatusEnum.STOPPED.getCode(), TaskStatusEnum.COMPLETED.getCode()))
                .orderByDesc(TbChatSessionTask::getUpdateTime)
                .last("limit 1"));
        if(lastTask == null) return;
        updateTaskStatus(lastTask.getId(), TaskStatusEnum.STOPPED.getCode());
    }

    private final String MATCH_SCENARIO_SYS_PROMPT = """
            你是一个“意图场景分类器”。
            系统会提供一个“意图场景列表”，其中每个场景包含：
            1、场景编号（数字）
            2、场景描述（文本）
            
            你需要根据用户输入内容，对照意图描述进行语义匹配，选出最符合的场景编号。
            
            规则：
            
            1、输出为单个数字（场景编号）
            2、未匹配则返回 0
            3、匹配基于语义，而非关键词
            4、意图列表将持续增加，你需要按给定列表动态适配
            5、置信度门槛
            必须把“下单、创建订单、修改订单、把…改成…”这类明确动词或指令完整说出来，才触发场景 3 或 4。
            仅出现数字、商品编号、件数、重量等碎片信息，不带上述动词，一律视为“弱关联”，返回 0。
            
            场景列表示例（示例可以随时扩展）：
            %s
            
            输出示例（仅数字）：
            
            用户输入 → 输出
            “帮我注册一个账号” → 1
            “我密码忘了帮帮我” → 2
            “你好呀” → 0
            """;
    private Integer matchScenario(String message) {
        List<TbTaskFieldTemplate> tbTaskFieldTemplates = tbTaskFieldTemplateService.listForEnable();
        if(CollectionUtils.isEmpty(tbTaskFieldTemplates)) return 0;

        StringJoiner scenarioList = new StringJoiner("\n");
        for (TbTaskFieldTemplate tbTaskFieldTemplate : tbTaskFieldTemplates) {
            String scenario = "场景编号：%s\t场景描述：%s".formatted(tbTaskFieldTemplate.getScenarioCode(), tbTaskFieldTemplate.getDescription());
            scenarioList.add(scenario);
        }
        String scenarioCode = largeLanguageModel.chatCompletion(MATCH_SCENARIO_SYS_PROMPT.formatted(scenarioList), message);
        if(StringUtils.isBlank(scenarioCode)) return 0;
        return Integer.parseInt(scenarioCode);
    }

    @Override
    public Page<TbChatSessionTaskVo> pageTask(PageForm pageDto) {
        Page<TbChatSessionTaskVo> page = new Page<>(pageDto.getCurrentPage(), pageDto.getPageSize());
        return tbChatSessionTaskMapper.pageTask(page, pageDto);
    }
}
