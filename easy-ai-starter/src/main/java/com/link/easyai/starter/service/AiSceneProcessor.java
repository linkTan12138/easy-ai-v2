package com.link.easyai.starter.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.link.easyai.starter.domain.dto.AiSceneProcessorDto;
import com.link.easyai.starter.domain.dto.CollectParamDto;
import com.link.easyai.starter.domain.dto.CollectParamFieldDto;
import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import com.link.easyai.starter.domain.exception.BusinessException;
import com.link.easyai.starter.domain.vo.AiChatResponseVo;
import com.link.easyai.starter.domain.vo.CollectFieldValidVo;
import com.link.easyai.starter.domain.vo.CollectParamVo;
import com.link.easyai.starter.mapper.TbChatSessionTaskMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public interface AiSceneProcessor {


    AiChatResponseVo process(AiSceneProcessorDto processorDto) throws Exception;

    LargeLanguageModel getLargeLanguageModel();
    TbChatSessionTaskMapper getTaskMapper();

    String SYS_PROMPT = """
    你现在的任务是：从用户提供的自然语言内容中，精准提取字段，并按严格的 JSON 格式返回。
    
    ⚠️ 必须遵守的总规则：
    1. **key 必须是字段名，value 必须符合字段类型。**
    2. **不要把示例当成值，不要胡乱猜测。**
    3. **没有值的字段绝对不能返回。**
    4. **输出中严禁出现说明、文字、解释，只能返回 JSON。**
    
    -------------------------
    【可提取字段定义】
    
    %s
    
    -------------------------
    【最终输出要求】
    - 仅返回一个 JSON 对象，格式如下：
      {"字段名": 字段值, ...}
    - 字段值必须符合字段类型（全部为 String）。
    - 不出现空字段、不出现未定义字段。
    - 未识别字段不要乱生成。
    
    请严格按以上规则提取并返回 JSON。
                
        """;
    default  <T> CollectParamVo collectParam(CollectParamDto collectParamDto, Class<T> clazz) {
        TbChatSessionTask task = getTaskMapper().selectById(collectParamDto.getTaskId());
        if(task == null) throw new BusinessException("数据异常，任务不存在！");

        String fieldListJson = task.getFieldList();
        if(StringUtils.isBlank(fieldListJson))  throw new BusinessException("数据异常，字段列表为空！");

        List<CollectParamFieldDto> fieldList = JSONUtil.parseArray(fieldListJson).toList(CollectParamFieldDto.class);

        JSONObject storeParam = new JSONObject();
        String extraContent = task.getExtraContent();
        if (StringUtils.isNotBlank(extraContent)) {
            storeParam = JSONUtil.parseObj(extraContent);
        }


        String result = getLargeLanguageModel().chatCompletion(SYS_PROMPT.formatted(tranPrompt(fieldList,storeParam)), collectParamDto.getMessage());

        Map<String,String> errorFieldMap = new HashMap<>();
        if(StringUtils.isNotBlank(result)) {
            JSONObject resultObject = JSONUtil.parseObj(result);
            for (Map.Entry<String, Object> objectEntry : resultObject.entrySet()) {
                if(objectEntry.getValue() == null || StringUtils.isBlank(objectEntry.getValue().toString())) continue;
                CollectFieldValidVo passed = validField(objectEntry.getKey(), objectEntry.getValue());
                if(!passed.isPassed()) {
                    errorFieldMap.put(objectEntry.getKey(), passed.getErrMsg());
                    continue;
                }
                storeParam.put(objectEntry.getKey(), objectEntry.getValue());
            }
        }

        StringJoiner notCollectParam = new StringJoiner("\n");
        StringJoiner hasCollectParam = new StringJoiner("\n");
        List<String> hasCollectFields = new ArrayList<>();
        for (CollectParamFieldDto field : fieldList) {
            // 未收集的参数字段
            if(!storeParam.containsKey(field.getField())) {
                if(StringUtils.isNotBlank(field.getPremiseFields())) {
                    List<String> requiredFields = fieldList.stream().filter(item -> item.getRequired() != null && item.getRequired() == 0).map(CollectParamFieldDto::getField).toList();
                    List<String> premiseFields = JSONUtil.parseArray(field.getPremiseFields()).toList(String.class);
                    premiseFields.removeAll(requiredFields);
                    JSONObject finalStoreParam = storeParam;
                    if(premiseFields.stream().anyMatch(premiseField -> !finalStoreParam.containsKey(premiseField))) continue;
                }
                if(errorFieldMap.containsKey(field.getField())) {
                    notCollectParam.add("\t-"+field.getFieldName() + "：\t" + errorFieldMap.get(field.getField()));
                } else {
                    notCollectParam.add("\t-"+field.getFieldName() + "：\t例如：" + field.getExample());
                }
            } else {
                hasCollectParam.add("\t-"+field.getFieldName() + "：\t" + transformValue(field,storeParam));
                hasCollectFields.add(field.getField());
            }
        }

        task.setExtraContent(storeParam.toString());
        getTaskMapper().updateById(task);

        T bean = null;
        if(storeParam != null) {
            bean = storeParam.toBean(clazz);
        }

        List<String> requireFields = fieldList.stream().filter(item -> item.getRequired() == null || item.getRequired() == 1).map(CollectParamFieldDto::getField).toList();
        if(hasCollectFields.containsAll(requireFields)) {
            return CollectParamVo.success(bean);
        }
        StringJoiner resultMsg = new StringJoiner("\n");
        String scenarioMsg = transScenarioMsg();
        if (StringUtils.isNotBlank(scenarioMsg)) {
            resultMsg.add(scenarioMsg);
            resultMsg.add("\n");
        }
        if(StringUtils.isNotBlank(hasCollectParam.toString())) resultMsg.add("已收集的参数：\n" + hasCollectParam+"\n");
        if(StringUtils.isNotBlank(notCollectParam.toString())) resultMsg.add("请继续提供以下字段：\n" + notCollectParam);
        return CollectParamVo.fail(resultMsg.toString()).data(bean);
    }

    String transScenarioMsg();

    private String transformValue(CollectParamFieldDto field, JSONObject storeParam) {
        String enums = field.getEnums();
        String value = storeParam.get(field.getField()).toString();
        if(StringUtils.isBlank(enums)) return value;
        List<Map> enumMaps = JSONUtil.parseArray(enums).toList(Map.class);
        for (Map enumMap : enumMaps) {
            if(enumMap.values().iterator().next().toString().equals(value)) return enumMap.keySet().iterator().next().toString();
        }
        return value;
    }


    private String tranPrompt(List<CollectParamFieldDto> fieldList, JSONObject storeParam) {
        StringJoiner joiner = new StringJoiner("\n");
        for (CollectParamFieldDto field : fieldList) {
            if(StringUtils.isNotBlank(field.getPremiseFields())) {
                List<String> requiredFields = fieldList.stream().filter(item -> item.getRequired() != null && item.getRequired() == 0).map(CollectParamFieldDto::getField).toList();
                List<String> premiseFields = JSONUtil.parseArray(field.getPremiseFields()).toList(String.class);
                premiseFields.removeAll(requiredFields);
                if(premiseFields.stream().anyMatch(premiseField -> !storeParam.containsKey(premiseField))) continue;
            }

            joiner.add("字段名：" + field.getField());
            joiner.add("字段类型：" + field.getFieldType());
            joiner.add(field.getJudgmentLogic());
            if(StringUtils.isNotBlank(field.getExample())) joiner.add("示例：" + field.getExample());
            if(StringUtils.isNotBlank(field.getEnums())) {
                List<Map> jsonArray = JSONUtil.parseArray(field.getEnums()).toList(Map.class);
                if(jsonArray != null && !jsonArray.isEmpty()) {
                    String enums = transEnums(jsonArray);
                    joiner.add("枚举值：" + enums);
                    Map fieldEnum = jsonArray.get(0);
                    joiner.add("客户回复文字时，需返回对应编码（例如回复“" + fieldEnum.get("label") + "”返回“" + fieldEnum.get("value") + "”）");
                }
            }
            joiner.add("\n");
        }

        return joiner.toString();
    }

    private String transEnums(List<Map> jsonArray) {
        StringJoiner joiner = new StringJoiner(",");
        for (Map map : jsonArray) {
            joiner.add(map.get("label")+":"+map.get("value"));
        }
        return joiner.toString();
    }

    CollectFieldValidVo validField(String field, Object value);

}
