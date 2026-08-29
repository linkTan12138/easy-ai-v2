package com.link.easyai.starter.engine.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.config.LLMlHolder;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.llm.LlmCallException;
import com.link.easyai.starter.engine.llm.LlmClient;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultExtractionEngine}: LLM call, JSON parsing,
 * markdown fence tolerance, and pending-field filtering.
 */
class DefaultExtractionEngineTest {

    private LlmClient llmClient;
    private LLMlHolder llmHolder;
    private DefaultExtractionEngine engine;
    private TaskState state;

    private static final List<FieldDefinition> PENDING = List.of(
            FieldDefinition.builder().code("channel").name("渠道").type(FieldType.STRING).build(),
            FieldDefinition.builder().code("country").name("目的国").type(FieldType.STRING).build());

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        llmHolder = mock(LLMlHolder.class);
        when(llmHolder.getActiveModelName()).thenReturn("kimi");
        engine = new DefaultExtractionEngine(new DefaultPromptBuilder(), new ObjectMapper(), llmClient, llmHolder);
        Map<String, FieldState> fields = new HashMap<>();
        state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(fields).build();
    }

    @Test
    @DisplayName("正常 JSON 响应被正确解析")
    void parsesNormalJson() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("{\"fields\":{\"channel\":\"DHL\",\"country\":\"US\"},\"reason\":\"ok\"}");

        ExtractionResult result = engine.extract("走DHL到美国", PENDING, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getFields().size());
        assertEquals("DHL", result.getFields().get("channel"));
        assertEquals("US", result.getFields().get("country"));
        assertEquals("ok", result.getReason());
    }

    @Test
    @DisplayName("markdown 代码块包裹的 JSON 被剥离解析")
    void stripsMarkdownFences() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("```json\n{\"fields\":{\"channel\":\"DHL\"},\"reason\":\"\"}\n```");

        ExtractionResult result = engine.extract("DHL", PENDING, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertEquals("DHL", result.getFields().get("channel"));
    }

    @Test
    @DisplayName("LLM 返回非 pending 字段时被丢弃")
    void dropsNonPendingFields() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("{\"fields\":{\"channel\":\"DHL\",\"unknownField\":\"x\"}}");

        ExtractionResult result = engine.extract("DHL", PENDING, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFields().size());
        assertFalse(result.getFields().containsKey("unknownField"));
    }

    @Test
    @DisplayName("已收集字段被用户重新提供（更正）时不再被丢弃，且返回新值")
    void acceptsCorrectionOfCollectedField() {
        // waybillNo 上一轮已收集为 JT234222，本轮用户更正为 JT23455
        Map<String, FieldState> fields = new HashMap<>();
        fields.put("waybillNo", FieldState.builder()
                .field("waybillNo")
                .status(FieldStatus.VALID)
                .value("JT234222")
                .build());
        state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(fields).build();

        List<FieldDefinition> pending = List.of(
                FieldDefinition.builder().code("channel").name("渠道").type(FieldType.STRING).build());
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("{\"fields\":{\"waybillNo\":\"JT23455\"},\"reason\":\"用户更正单号\"}");

        ExtractionResult result = engine.extract("单号写错了，应该是 JT23455", pending, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertEquals("JT23455", result.getFields().get("waybillNo"));
    }

    @Test
    @DisplayName("空值和空白字符串被过滤")
    void dropsEmptyValues() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("{\"fields\":{\"channel\":\"\",\"country\":null}}");

        ExtractionResult result = engine.extract("无信息", PENDING, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertTrue(result.getFields().isEmpty());
    }

    @Test
    @DisplayName("数字/布尔/数组被转换为标准 Java 类型")
    void convertsJsonTypes() {
        List<FieldDefinition> fields = List.of(
                FieldDefinition.builder().code("count").name("数量").type(FieldType.INTEGER).build(),
                FieldDefinition.builder().code("flag").name("标记").type(FieldType.BOOLEAN).build(),
                FieldDefinition.builder().code("items").name("明细").type(FieldType.STRING_LIST).build());
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("{\"fields\":{\"count\":5,\"flag\":true,\"items\":[\"a\",\"b\"]}}");

        ExtractionResult result = engine.extract("5个", fields, List.of(), state, null);

        assertEquals(5L, result.getFields().get("count"));
        assertEquals(Boolean.TRUE, result.getFields().get("flag"));
        assertInstanceOf(java.util.List.class, result.getFields().get("items"));
        assertEquals(2, ((java.util.List<?>) result.getFields().get("items")).size());
    }

    @Test
    @DisplayName("LLM 直接返回字段对象（无 fields 包装）也能解析")
    void parsesDirectObjectResponse() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenReturn("{\"channel\":\"DHL\"}");

        ExtractionResult result = engine.extract("DHL", PENDING, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertEquals("DHL", result.getFields().get("channel"));
    }

    @Test
    @DisplayName("LLM 返回空响应时返回失败")
    void emptyLlmResponseFails() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString())).thenReturn("  ");

        ExtractionResult result = engine.extract("DHL", PENDING, List.of(), state, null);

        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("LLM 返回非 JSON 文本时返回失败")
    void nonJsonResponseFails() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString())).thenReturn("抱歉我不明白");

        ExtractionResult result = engine.extract("DHL", PENDING, List.of(), state, null);

        assertFalse(result.isSuccess());
        assertEquals("抱歉我不明白", result.getRawResponse());
    }

    @Test
    @DisplayName("LLM 抛异常时返回失败而非抛出")
    void llmExceptionFailsGracefully() {
        when(llmClient.chatCompletion(anyString(), anyString(), anyString()))
                .thenThrow(new LlmCallException("timeout", false));

        ExtractionResult result = engine.extract("DHL", PENDING, List.of(), state, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("用户消息为空时返回空结果（成功）")
    void emptyUserMessageReturnsEmpty() {
        ExtractionResult result = engine.extract("  ", PENDING, List.of(), state, null);

        assertTrue(result.isSuccess());
        assertTrue(result.getFields().isEmpty());
    }
}
