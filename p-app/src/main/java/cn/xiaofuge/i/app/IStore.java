package cn.xiaofuge.i.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 健身房会籍数据中心：会员卡/教练/课程/会籍/统计 */
@Component
public class IStore {

    /** 会员卡种：名称/价格(元)/时长(月)/权益说明 */
    static final Map<String, Object[]> PLANS = new LinkedHashMap<>();
    static {
        PLANS.put("月卡", new Object[]{399.0, 1, "全器械区 + 每日 1 节团课"});
        PLANS.put("季卡", new Object[]{999.0, 3, "全器械区 + 团课不限次 + 1 次体测"});
        PLANS.put("年卡", new Object[]{3299.0, 12, "全器械区 + 团课不限次 + 4 次体测 + 泳池"});
        PLANS.put("私教月包", new Object[]{2800.0, 1, "年卡权益 + 8 节私教课"});
    }

    /** 教练：编号/姓名/专长/课时费(元/节) */
    static final Map<String, Object[]> COACHES = new LinkedHashMap<>();
    static {
        COACHES.put("T01", new Object[]{"周教练", "力量训练 / 减脂", 300.0});
        COACHES.put("T02", new Object[]{"林教练", "瑜伽 / 普拉提", 280.0});
        COACHES.put("T03", new Object[]{"吴教练", "搏击操 / 功能性训练", 320.0});
    }

    /** 团课表：时间/课程/教练/容量余位 */
    static final List<String[]> CLASSES = new ArrayList<>(List.of(
        new String[]{"周三 19:00", "燃脂搏击操", "T03 吴教练", "6"},
        new String[]{"周四 19:30", "流瑜伽", "T02 林教练", "3"},
        new String[]{"周六 10:00", "杠铃塑形", "T01 周教练", "8"},
        new String[]{"周日 10:30", "普拉提核心", "T02 林教练", "4"}
    ));

    public static class Member {
        public String id; public String name; public String plan;
        public String start; public String end; public String status; // 有效 / 已过期 / 待激活
    }

    public static class Booking {
        public String id; public String member; public String classTime;
        public String className; public String coach;
    }

    public final List<Member> members = new ArrayList<>();
    public final List<Booking> bookings = new ArrayList<>();
    private int memberSeq = 5001;
    private int bookingSeq = 7001;

    public IStore() { seed(); }

    private void seed() {
        members.add(m("钱女士", "年卡", "2026-01-15", "2027-01-14", "有效"));
        members.add(m("孙先生", "月卡", "2026-09-01", "2026-09-30", "有效"));
        members.add(m("李小姐", "季卡", "2026-04-10", "2026-07-09", "已过期"));

        bookings.add(b("李小姐", "周三 19:00", "燃脂搏击操", "T03 吴教练"));
        bookings.add(b("钱女士", "周六 10:00", "杠铃塑形", "T01 周教练"));
    }

    private Member m(String name, String plan, String start, String end, String status) {
        Member x = new Member(); x.id = "M" + memberSeq++; x.name = name; x.plan = plan;
        x.start = start; x.end = end; x.status = status; return x;
    }

    private Booking b(String member, String classTime, String className, String coach) {
        Booking x = new Booking(); x.id = "B" + bookingSeq++; x.member = member;
        x.classTime = classTime; x.className = className; x.coach = coach; return x;
    }

    /** 卡种列表 */
    public Map<String, Object> planList() {
        List<Map<String, Object>> list = new ArrayList<>();
        PLANS.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", k); m.put("price", v[0]); m.put("months", v[1]); m.put("benefits", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("plans", list);
        r.put("coaches", coachList());
        return r;
    }

    private List<Map<String, Object>> coachList() {
        List<Map<String, Object>> list = new ArrayList<>();
        COACHES.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", k); m.put("name", v[0]); m.put("specialty", v[1]);
            m.put("sessionFee", v[2]); list.add(m); });
        return list;
    }

    /** 团课表 */
    public Map<String, Object> classList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String[] c : CLASSES) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("time", c[0]); m.put("name", c[1]); m.put("coach", c[2]);
            m.put("seatsLeft", Long.parseLong(c[3]));
            list.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("classes", list);
        r.put("note", "团课对有效会员免费，私教课按教练课时费单独计费");
        return r;
    }

    /** 办卡：姓名与卡种校验 */
    public synchronized Map<String, Object> join(String name, String plan, String phone) {
        if (name == null || name.isBlank())
            return Map.of("ok", false, "msg", "请提供办卡人姓名");
        Object[] p = PLANS.get(plan);
        if (p == null) return Map.of("ok", false, "msg", "卡种 " + plan + " 不存在，可选：" + String.join("/", PLANS.keySet()));
        Member x = new Member(); x.id = "M" + memberSeq++; x.name = name; x.plan = plan;
        x.start = "2026-09-25"; x.status = "有效";
        int months = (Integer) p[1];
        x.end = months >= 12 ? "2027-09-24" : months >= 3 ? "2026-12-24" : "2026-10-24";
        members.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("memberId", x.id); r.put("name", name); r.put("plan", plan);
        r.put("price", p[0]); r.put("period", x.start + " ~ " + x.end); r.put("benefits", p[2]);
        if (phone != null && !phone.isBlank()) r.put("phone", phone);
        r.put("msg", "办卡成功！会员号 " + x.id + "，" + plan + " ¥" + p[0] + "，有效期 " + x.start + " ~ " + x.end + "，欢迎加入");
        return r;
    }

    /** 会籍查询 */
    public Map<String, Object> memberInfo(String memberId) {
        Member x = members.stream().filter(o -> o.id.equalsIgnoreCase(memberId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "会员 " + memberId + " 不存在，当前共 " + members.size() + " 名会员");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("memberId", x.id); r.put("name", x.name);
        r.put("plan", x.plan); r.put("period", x.start + " ~ " + x.end);
        r.put("status", x.status);
        Object[] p = PLANS.get(x.plan);
        r.put("benefits", p != null ? p[2] : "");
        if ("已过期".equals(x.status)) r.put("msg", "会籍已过期，续费享 9 折优惠");
        return r;
    }

    /** 团课预约 */
    public synchronized Map<String, Object> book(String memberName, String classTime) {
        if (memberName == null || memberName.isBlank())
            return Map.of("ok", false, "msg", "请提供预约人姓名");
        String[] target = null;
        for (String[] c : CLASSES) if (c[0].equalsIgnoreCase(classTime)) { target = c; break; }
        if (target == null) return Map.of("ok", false, "msg", "未找到 " + classTime + " 的团课，可选：" + CLASSES.stream().map(c -> c[0] + " " + c[1]).collect(Collectors.joining("；")));
        int seats = Integer.parseInt(target[3]);
        if (seats <= 0) return Map.of("ok", false, "msg", target[0] + " " + target[1] + " 已满员，可预约其他时段");
        target[3] = String.valueOf(seats - 1);
        Booking x = b(memberName, target[0], target[1], target[2]);
        bookings.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("bookingId", x.id); r.put("member", memberName);
        r.put("classTime", x.classTime); r.put("className", x.className); r.put("coach", x.coach);
        r.put("seatsLeft", target[3]);
        r.put("msg", "预约成功！预约号 " + x.id + "，" + x.classTime + " " + x.className + "（" + x.coach + "），请提前 10 分钟到场");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byPlan = new LinkedHashMap<String, Object>();
        for (String plan : PLANS.keySet()) {
            long n = members.stream().filter(o -> plan.equals(o.plan)).count();
            if (n > 0) byPlan.put(plan, n + " 人");
        }
        long valid = members.stream().filter(o -> "有效".equals(o.status)).count();
        long expired = members.stream().filter(o -> "已过期".equals(o.status)).count();
        double revenue = members.stream()
                .filter(o -> "有效".equals(o.status))
                .mapToDouble(o -> (Double) PLANS.get(o.plan)[0]).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalMembers", members.size());
        r.put("valid", valid); r.put("expired", expired);
        r.put("activeRevenue", revenue);
        r.put("byPlan", byPlan);
        r.put("bookings", bookings.size());
        r.put("advice", "瑜伽/普拉提预约占比高建议加开晚间时段；过期会员 11 人次可推送 9 折续费；周末上午团课满座率高可增设动感单车");
        return r;
    }
}
