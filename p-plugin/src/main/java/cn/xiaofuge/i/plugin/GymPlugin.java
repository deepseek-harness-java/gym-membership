package cn.xiaofuge.i.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 健身房会籍管家插件：把 gym-membership REST API 注册为 DSH Agent 工具 */
public class GymPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "gym-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public GymPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new PlanListTool(),
                new ClassListTool(),
                new JoinTool(),
                new MemberInfoTool(),
                new BookTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("gym-capabilities", 20, """
                ## AI 健身房会籍管家（连锁健身房运营 · 2026-09-25）
                - 查卡种 → plan_list（4 种卡价格与权益：月卡399/季卡999/年卡3299/私教月包2800；
                  含 3 位教练专长与课时费）
                - 查团课 → class_list（本周团课表：时间/课程/教练/余位）
                - 办卡 → join（name/plan 必填，phone 可填；
                  必须先复述卡种、价格、权益、有效期请顾客确认后才能调用；成功报会员号与有效期）
                - 会籍查询 → member_info（memberId：M5001 格式；卡种/有效期/权益/状态）
                - 团课预约 → book（member/classTime 必填；成功报预约号并提醒提前 10 分钟到场）
                - 问运营 → stats（会员数/有效与过期/活跃会籍收入/分卡种分布/续费与排课建议）
                - 回答要求：
                  1) 办卡前必须复述要素（卡种/价格/权益/有效期）请顾客确认
                  2) 办卡与预约结果必报编号（会员号/预约号）
                  3) 训练强度、饮食等健康建议只做常识性提醒，不提供医疗建议
                  4) 价格与权益只转述工具返回，禁止编造未公示折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: gym tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("GYM_APP_BASE_URL", "http://127.0.0.1:18107")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class PlanListTool extends AbstractTool {
        @Override public String name() { return "plan_list"; }
        @Override public String description() {
            return "会员卡种列表：4 种卡的价格与权益（月卡/季卡/年卡/私教月包），含教练专长与课时费。"
                    + "推荐卡种、办卡前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/plans", args));
        }
    }

    private class ClassListTool extends AbstractTool {
        @Override public String name() { return "class_list"; }
        @Override public String description() {
            return "本周团课表：时间/课程名/教练/剩余名额。顾客问课程安排、想预约团课时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/classes", args));
        }
    }

    private class JoinTool extends AbstractTool {
        @Override public String name() { return "join"; }
        @Override public String description() {
            return "办卡入会：name（办卡人）/plan（卡种名）必填，phone 可选。"
                    + "必须先复述卡种、价格、权益与有效期经顾客确认后才能调用。成功返回会员号与有效期。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("name", stringSchema("办卡人姓名"))
                    .prop("plan", stringSchema("卡种：月卡 / 季卡 / 年卡 / 私教月包"))
                    .prop("phone", stringSchema("联系电话（可选）"))
                    .required("name", "plan")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"name\":\"" + json(str(args, "name"))
                    + "\",\"plan\":\"" + json(str(args, "plan"))
                    + "\",\"phone\":\"" + json(str(args, "phone")) + "\"}";
            return ok(post("/api/join", body, args));
        }
    }

    private class MemberInfoTool extends AbstractTool {
        @Override public String name() { return "member_info"; }
        @Override public String description() {
            return "会籍查询：memberId 必填（M5001 格式）。返回卡种/有效期/权益/状态（有效、已过期），过期时提示续费优惠。"
                    + "何时必须调用：顾客问自己的会籍、问有效期。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("memberId", stringSchema("会员号，如 M5001"))
                    .required("memberId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/member?memberId=" + java.net.URLEncoder.encode(str(args, "memberId"), StandardCharsets.UTF_8), args));
        }
    }

    private class BookTool extends AbstractTool {
        @Override public String name() { return "book"; }
        @Override public String description() {
            return "团课预约：member（预约人姓名）/classTime（团课时间，如 周三 19:00）必填。"
                    + "预约前先用 class_list 查余位。成功返回预约号并提醒提前 10 分钟到场。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("member", stringSchema("预约人姓名"))
                    .prop("classTime", stringSchema("团课时间，如 周三 19:00 / 周六 10:00"))
                    .required("member", "classTime")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"member\":\"" + json(str(args, "member"))
                    + "\",\"classTime\":\"" + json(str(args, "classTime")) + "\"}";
            return ok(post("/api/book", body, args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：会员总数/有效与过期/活跃会籍收入/分卡种分布/团课预约量/续费与排课建议。"
                    + "何时必须调用：问今天运营、问会员与收入。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
