package com.link.easyai.starter.engine.action;

import com.link.easyai.starter.engine.action.builtin.LoggingPostAction;
import com.link.easyai.starter.engine.config.ActionConfig;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.context.ActionContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultActionEngine}: main action execution,
 * post-action ordering, failure isolation and parameter merging.
 */
class DefaultActionEngineTest {

    private ActionRegistry registry;
    private DefaultActionEngine engine;
    private TaskState state;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        engine = new DefaultActionEngine(registry);
        state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(new HashMap<>()).build();
    }

    private AiTaskConfig config(String actionType, List<String> postActions, Map<String, Object> params) {
        return AiTaskConfig.builder()
                .taskType("T").version(1)
                .action(ActionConfig.builder()
                        .type(actionType)
                        .postActions(postActions)
                        .params(params)
                        .build())
                .build();
    }

    @Test
    @DisplayName("主动作成功后按顺序执行全部 postActions")
    void successExecutesPostActionsInOrder() {
        StringBuilder order = new StringBuilder();
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                order.append("main;");
                return ActionResult.success("done", null);
            }
        });
        registry.register(postAction("P1", order));
        registry.register(postAction("P2", order));

        ActionResult result = engine.execute(
                config("MAIN", List.of("P1", "P2"), null),
                state, Map.of("a", 1), new TaskContext());

        assertTrue(result.isSuccess());
        assertEquals("main;p1;p2;", order.toString());
    }

    @Test
    @DisplayName("主动作失败时不执行 postActions")
    void failedMainActionSkipsPostActions() {
        StringBuilder order = new StringBuilder();
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                order.append("main;");
                return ActionResult.fail("BIZ_ERROR", "业务失败");
            }
        });
        registry.register(postAction("P1", order));

        ActionResult result = engine.execute(
                config("MAIN", List.of("P1"), null),
                state, null, new TaskContext());

        assertFalse(result.isSuccess());
        assertEquals("main;", order.toString());
    }

    @Test
    @DisplayName("未注册的 action type 返回 ACTION_NOT_FOUND")
    void unregisteredActionFails() {
        ActionResult result = engine.execute(
                config("NOPE", null, null), state, null, new TaskContext());

        assertFalse(result.isSuccess());
        assertEquals("ACTION_NOT_FOUND", result.getErrorCode());
    }

    @Test
    @DisplayName("action 未配置时返回 ACTION_NOT_CONFIGURED")
    void missingActionConfigFails() {
        AiTaskConfig noAction = AiTaskConfig.builder().taskType("T").version(1).build();

        ActionResult result = engine.execute(noAction, state, null, new TaskContext());

        assertFalse(result.isSuccess());
        assertEquals("ACTION_NOT_CONFIGURED", result.getErrorCode());
    }

    @Test
    @DisplayName("ActionExecutor 抛异常时包装为 ACTION_ERROR，不向外抛出")
    void executorExceptionWrapped() {
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                throw new IllegalStateException("boom");
            }
        });

        ActionResult result = engine.execute(
                config("MAIN", null, null), state, null, new TaskContext());

        assertFalse(result.isSuccess());
        assertEquals("ACTION_ERROR", result.getErrorCode());
    }

    @Test
    @DisplayName("ActionExecutor 返回 null 时包装为 ACTION_ERROR")
    void nullResultWrapped() {
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                return null;
            }
        });

        ActionResult result = engine.execute(
                config("MAIN", null, null), state, null, new TaskContext());

        assertFalse(result.isSuccess());
        assertEquals("ACTION_ERROR", result.getErrorCode());
    }

    @Test
    @DisplayName("单个 postAction 失败不影响主动作结果和其他 postActions")
    void postActionFailureIsolated() {
        StringBuilder order = new StringBuilder();
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                return ActionResult.success("done", null);
            }
        });
        registry.register(new PostActionExecutor() {
            @Override public String type() { return "BAD"; }
            @Override public void execute(ActionContext ctx) {
                order.append("bad;");
                throw new RuntimeException("post boom");
            }
        });
        registry.register(postAction("GOOD", order));

        ActionResult result = engine.execute(
                config("MAIN", List.of("BAD", "GOOD"), null),
                state, null, new TaskContext());

        assertTrue(result.isSuccess()); // main action unaffected
        assertEquals("bad;good;", order.toString());
    }

    @Test
    @DisplayName("未注册的 postAction 被跳过且不报错")
    void unregisteredPostActionSkipped() {
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                return ActionResult.success("done", null);
            }
        });

        ActionResult result = engine.execute(
                config("MAIN", List.of("NOT_REGISTERED"), null),
                state, null, new TaskContext());

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("config.params 与映射参数合并，字段映射值优先")
    void configParamsMergedButFieldValuesWin() {
        AtomicInteger check = new AtomicInteger(-1);
        Map<String, Object> configParams = new HashMap<>();
        configParams.put("fixed", "c");
        configParams.put("overlapped", "from-config");

        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                check.set(0);
                assertEquals("c", ctx.getParameters().get("fixed"));
                assertEquals("from-field", ctx.getParameters().get("overlapped"));
                return ActionResult.success("done", null);
            }
        });

        ActionResult result = engine.execute(
                config("MAIN", null, configParams),
                state,
                Map.of("overlapped", "from-field"),
                new TaskContext());

        assertTrue(result.isSuccess());
        assertEquals(0, check.get());
    }

    @Test
    @DisplayName("ActionContext 携带 taskId / state / config / taskContext")
    void actionContextCarriesEverything() {
        TaskContext taskContext = TaskContext.builder().tenantId(99L).build();
        registry.register(new ActionExecutor() {
            @Override public String type() { return "MAIN"; }
            @Override public ActionResult execute(ActionContext ctx) {
                assertEquals("1", ctx.getTaskId());
                assertNotNull(ctx.getState());
                assertNotNull(ctx.getConfig());
                assertEquals(99L, ctx.getTaskContext().getTenantId());
                return ActionResult.success("done", null);
            }
        });

        engine.execute(config("MAIN", null, null), state, null, taskContext);
    }

    // ---------- LoggingPostAction (built-in Phase 7) ----------

    @Test
    @DisplayName("内置 LOG postAction 只做审计，不改任务状态（终态由 Engine 管理）")
    void loggingPostActionDoesNotChangeStatus() {
        LoggingPostAction logging = new LoggingPostAction();

        assertEquals("LOG", logging.type());

        AiTaskConfig cfg = config("MAIN", null, null);
        ActionContext ctx = ActionContext.builder()
                .taskId("1").config(cfg).state(state)
                .parameters(new HashMap<>()).build();

        state.setStatus(com.link.easyai.starter.engine.state.TaskStatus.EXECUTING);
        logging.execute(ctx);

        // Post-action is pure audit: the engine (not the post-action) owns
        // the terminal COMPLETED transition
        assertEquals(com.link.easyai.starter.engine.state.TaskStatus.EXECUTING, state.getStatus());
    }

    /** Helper: post action that records execution order */
    private PostActionExecutor postAction(String name, StringBuilder order) {
        return new PostActionExecutor() {
            @Override public String type() { return name; }
            @Override public void execute(ActionContext ctx) {
                order.append(name.toLowerCase()).append(";");
            }
        };
    }
}
