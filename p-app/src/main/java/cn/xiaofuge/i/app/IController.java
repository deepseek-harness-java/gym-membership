package cn.xiaofuge.i.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 健身房会籍管家 REST 接口。
 * 提供：卡种列表 / 团课表 / 办卡 / 会籍查询 / 团课预约 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class IController {

    private final IStore store;

    public IController(IStore store) {
        this.store = store;
    }

    /** 卡种列表（含教练信息） */
    @GetMapping("/plans")
    public Map<String, Object> plans() {
        return store.planList();
    }

    /** 团课表 */
    @GetMapping("/classes")
    public Map<String, Object> classes() {
        return store.classList();
    }

    /** 办卡 */
    @PostMapping("/join")
    public Map<String, Object> join(@RequestBody Map<String, String> body) {
        return store.join(body.getOrDefault("name", ""), body.getOrDefault("plan", ""),
                body.getOrDefault("phone", ""));
    }

    /** 会籍查询 */
    @GetMapping("/member")
    public Map<String, Object> memberInfo(@RequestParam(required = false) String memberId) {
        return store.memberInfo(memberId == null ? "" : memberId);
    }

    /** 团课预约 */
    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody Map<String, String> body) {
        return store.book(body.getOrDefault("member", ""), body.getOrDefault("classTime", ""));
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
