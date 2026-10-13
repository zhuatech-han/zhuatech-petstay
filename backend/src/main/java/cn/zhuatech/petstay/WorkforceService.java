// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 门店员工、班次与逐日责任人；跨店检查冲突，权限不提升为平台运营。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class WorkforceService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final ApplicationEventPublisher events;

  /** 使用业务共用写锁和实时身份，排班与订单版本原子变化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public WorkforceService(
      Store db,
      AccessService access,
      AdminService admin,
      Clock clock,
      ApplicationEventPublisher events) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
    this.events = events;
  }

  void begin() {
    admin.lock();
    access.require("portal");
  }

  void allowed(boolean ok) {
    if (!ok) throw new Problem(403, "OUT_OF_SCOPE");
  }

  LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of(db.get(Department.class, 1L).zone)));
  }

  void version(RecordBase r, Map<String, Object> b) {
    Rules.check(r.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  boolean activeAccount(Long id) {
    var a = db.get(Account.class, id);
    return a.enabled
        && db.get(Department.class, a.departmentId).enabled
        && db.get(AccessRole.class, a.roleId).permissions.contains("portal");
  }

  ShopStaff membership(Long provider, Long account) {
    var list =
        db.query(
            ShopStaff.class,
            "from ShopStaff where providerId=?1 and accountId=?2",
            provider,
            account);
    return list.isEmpty() ? null : list.getFirst();
  }

  boolean member(Provider p, Long account) {
    if (!activeAccount(account) || !activeAccount(p.accountId)) return false;
    var s = membership(p.id, account);
    return p.accountId.equals(account) || (s != null && s.status.equals("ACTIVE"));
  }

  boolean manager(Provider p, Long account) {
    if (!member(p, account)) return false;
    var s = membership(p.id, account);
    return p.accountId.equals(account) || s.role.equals("MANAGER");
  }

  boolean managed(Long provider) {
    var policies = db.query(ShopPolicy.class, "from ShopPolicy where providerId=?1", provider);
    return !policies.isEmpty() && policies.getFirst().workforceEnabled;
  }

  boolean visible(CareOrder o, Long account) {
    var p = db.get(Provider.class, o.providerId);
    return member(p, account)
        && (manager(p, account)
            || db.count(
                    "select count(a) from CareAssignment a where a.orderId=?1 and a.accountId=?2",
                    o.id,
                    account)
                > 0);
  }

  boolean coordinate(CareOrder o) {
    return manager(db.get(Provider.class, o.providerId), access.current().id);
  }

  boolean taskActor(CareOrder o, LocalDate date) {
    var id = access.current().id;
    if (!o.managedCare) return o.providerAccountId.equals(id);
    return member(db.get(Provider.class, o.providerId), id)
        && db.count(
                "select count(a) from CareAssignment a where a.orderId=?1 and a.accountId=?2 and a.taskDate=?3",
                o.id,
                id,
                date)
            > 0;
  }

  boolean photoActor(CareOrder o) {
    return taskActor(o, today());
  }

  /** 本人门店关系，包括待接受邀请；不列其他门店员工或客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object shops() {
    access.require("portal");
    Long id = access.current().id;
    var out = new ArrayList<Object>();
    for (var p : db.query(Provider.class, "from Provider where accountId=?1", id))
      out.add(
          Map.of(
              "id",
              p.id,
              "name",
              p.displayName,
              "role",
              "OWNER",
              "status",
              "ACTIVE",
              "canManage",
              true));
    for (var s :
        db.query(
            ShopStaff.class,
            "from ShopStaff where accountId=?1 and status in ?2",
            id,
            Set.of("INVITED", "ACTIVE"))) {
      var p = db.get(Provider.class, s.providerId);
      out.add(
          Map.of(
              "id",
              p.id,
              "name",
              p.displayName,
              "role",
              s.role,
              "status",
              s.status,
              "membershipId",
              s.id,
              "version",
              s.version,
              "canManage",
              manager(p, id)));
    }
    return out;
  }

  /** 负责人邀请已注册会员，员工确认后才获授权；店长不得任免其他店长。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object invite(Long provider, Map<String, Object> b) {
    begin();
    var p = db.get(Provider.class, provider);
    var actor = access.current().id;
    allowed(manager(p, actor));
    String role = Rules.text(b.get("role"), 20, true);
    Rules.check(Set.of("CARER", "MANAGER").contains(role), "INVALID_INPUT");
    allowed(role.equals("CARER") || p.accountId.equals(actor));
    var accounts =
        db.query(
            Account.class,
            "from Account where username=?1",
            Rules.text(b.get("username"), 60, true).toLowerCase(Locale.ROOT));
    Rules.check(!accounts.isEmpty() && activeAccount(accounts.getFirst().id), "MEMBER_UNAVAILABLE");
    Long account = accounts.getFirst().id;
    Rules.check(!account.equals(p.accountId), "OWNER_FIXED");
    var s = membership(provider, account);
    if (s != null) {
      version(s, b);
      Rules.check(Set.of("REVOKED", "REJECTED").contains(s.status), "INVITATION_EXISTS");
      s.version++;
    } else {
      Rules.check(
          db.count("select count(s) from ShopStaff s where s.providerId=?1", provider) < 100,
          "RESOURCE_LIMIT");
      s = new ShopStaff();
      s.providerId = provider;
      s.accountId = account;
      s.departmentId = p.departmentId;
      s.createdAt = clock.instant();
      db.save(s);
    }
    s.role = role;
    s.status = "INVITED";
    access.audit("STAFF_INVITE", s.id, p.departmentId);
    return s;
  }

  /** 本人接受、拒绝或退出；撤销前必须移交未完成责任，主人权限不可删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object staffAction(Long id, String action, Map<String, Object> b) {
    begin();
    var s = db.get(ShopStaff.class, id);
    var p = db.get(Provider.class, s.providerId);
    var actor = access.current().id;
    version(s, b);
    switch (action) {
      case "accept", "reject" -> {
        allowed(actor.equals(s.accountId));
        Rules.check(s.status.equals("INVITED") && activeAccount(p.accountId), "STATE_INVALID");
        s.status = action.equals("accept") ? "ACTIVE" : "REJECTED";
      }
      case "revoke", "leave" -> {
        if (action.equals("leave")) allowed(actor.equals(s.accountId));
        else allowed(manager(p, actor) && (s.role.equals("CARER") || p.accountId.equals(actor)));
        Rules.check(Set.of("ACTIVE", "INVITED").contains(s.status), "STATE_INVALID");
        Rules.check(!pendingAssignment(s.providerId, s.accountId, null), "ASSIGNMENTS_PENDING");
        s.status = "REVOKED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    s.version++;
    access.audit("STAFF_" + action.toUpperCase(Locale.ROOT), id, p.departmentId);
    return s;
  }

  boolean pendingAssignment(Long provider, Long account, StaffRoster excluding) {
    for (var a :
        db.query(
            CareAssignment.class,
            "from CareAssignment where providerId=?1 and accountId=?2",
            provider,
            account)) {
      var o = db.get(CareOrder.class, a.orderId);
      if (!Set.of("CONFIRMED", "IN_PROGRESS").contains(o.status)) continue;
      if (db.count(
              "select count(t) from CareTask t where t.orderId=?1 and t.taskDate=?2 and t.status=?3",
              o.id,
              a.taskDate,
              "PENDING")
          == 0) continue;
      if (excluding == null
          || (!a.taskDate.isBefore(excluding.startsOn)
              && a.taskDate.isBefore(excluding.endsOn)
              && (excluding.visitWindow.equals("DAY")
                  || excluding.visitWindow.equals(a.visitWindow)))) return true;
    }
    return false;
  }

  /** 负责人或员工本人发布班次；不修改其他门店班次，删除不能丢失未完成任务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object roster(Long provider, Long id, Map<String, Object> b) {
    begin();
    var p = db.get(Provider.class, provider);
    var actor = access.current().id;
    Long account = Rules.id(b.get("accountId"));
    allowed(member(p, actor) && (actor.equals(account) || manager(p, actor)));
    Rules.check(member(p, account), "STAFF_INACTIVE");
    var r = id == null ? new StaffRoster() : db.get(StaffRoster.class, id);
    if (id != null) {
      allowed(r.providerId.equals(provider) && r.accountId.equals(account));
      version(r, b);
      Rules.check(!pendingAssignment(provider, account, r), "ASSIGNMENTS_PENDING");
    }
    r.startsOn = LocalDate.parse(Rules.text(b.get("startsOn"), 10, true));
    r.endsOn = LocalDate.parse(Rules.text(b.get("endsOn"), 10, true));
    Rules.check(
        !r.startsOn.isBefore(today().minusDays(366))
            && r.endsOn.isAfter(r.startsOn)
            && ChronoUnit.DAYS.between(r.startsOn, r.endsOn) <= 90
            && r.startsOn.isBefore(today().plusDays(366)),
        "DATE_INVALID");
    r.visitWindow = Rules.text(b.get("visitWindow"), 20, true);
    Rules.check(Set.of("DAY", "AM", "PM", "EVENING").contains(r.visitWindow), "WINDOW_INVALID");
    for (var other :
        db.query(
            StaffRoster.class,
            "from StaffRoster where providerId=?1 and accountId=?2",
            provider,
            account))
      Rules.check(
          Objects.equals(other.id, id)
              || !r.startsOn.isBefore(other.endsOn)
              || !other.startsOn.isBefore(r.endsOn)
              || (!other.visitWindow.equals("DAY")
                  && !r.visitWindow.equals("DAY")
                  && !other.visitWindow.equals(r.visitWindow)),
          "ROSTER_CONFLICT");
    r.providerId = provider;
    r.accountId = account;
    r.departmentId = p.departmentId;
    if (id == null) {
      Rules.check(
          db.count("select count(r) from StaffRoster r where r.providerId=?1", provider) < 500,
          "RESOURCE_LIMIT");
      db.save(r);
    } else r.version++;
    access.audit("ROSTER_SAVE", r.id, p.departmentId);
    return r;
  }

  /** 删除不被待办责任占用的班次，历史照护记录仍保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deleteRoster(Long id, Map<String, Object> b) {
    begin();
    var r = db.get(StaffRoster.class, id);
    var p = db.get(Provider.class, r.providerId);
    var actor = access.current().id;
    allowed(member(p, actor) && (actor.equals(r.accountId) || manager(p, actor)));
    version(r, b);
    Rules.check(!pendingAssignment(r.providerId, r.accountId, r), "ASSIGNMENTS_PENDING");
    db.delete(r);
    access.audit("ROSTER_DELETE", id, p.departmentId);
    return Map.of("ok", true);
  }

  /** 排班模式仅冻结到新订单，旧订单不改为员工模式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object policy(Long provider, Map<String, Object> b) {
    begin();
    var p = db.get(Provider.class, provider);
    allowed(p.accountId.equals(access.current().id));
    var list = db.query(ShopPolicy.class, "from ShopPolicy where providerId=?1", provider);
    var s = list.isEmpty() ? new ShopPolicy() : list.getFirst();
    if (s.id != null) {
      version(s, b);
      s.version++;
    }
    s.providerId = provider;
    s.departmentId = p.departmentId;
    s.workforceEnabled = Rules.flag(b.get("workforceEnabled"));
    if (s.id == null) db.save(s);
    access.audit("SHOP_POLICY", provider, p.departmentId);
    return s;
  }

  void capacity(Long account, Long order, LocalDate date, String win) {
    var ids = new HashSet<Long>();
    var wins = new ArrayList<String>();
    for (var a :
        db.query(
            CareAssignment.class,
            "from CareAssignment where accountId=?1 and taskDate=?2",
            account,
            date)) {
      var o = db.get(CareOrder.class, a.orderId);
      if (!o.id.equals(order)
          && Set.of("CONFIRMED", "IN_PROGRESS", "REVIEW", "DISPUTED").contains(o.status)
          && !(o.kind.equals("BOARDING") && o.departedAt != null)) {
        ids.add(o.id);
        wins.add(a.visitWindow);
      }
    }
    for (var o :
        db.query(
            CareOrder.class,
            "from CareOrder where providerAccountId=?1 and managedCare=false and status in ?2",
            account,
            MarketService.OCCUPY)) {
      if (!o.id.equals(order)
          && !date.isBefore(o.startsOn)
          && (date.isBefore(o.endsOn) || (o.arrivedAt != null && o.departedAt == null))
          && !(o.kind.equals("BOARDING") && o.departedAt != null)
          && ids.add(o.id)) wins.add(o.kind.equals("BOARDING") ? "DAY" : o.visitWindow);
    }
    boolean ok =
        win.equals("DAY")
            ? wins.stream().noneMatch(w -> !w.equals("DAY")) && wins.size() < 5
            : wins.stream().noneMatch(w -> w.equals("DAY") || w.equals(win));
    Rules.check(ok, "STAFF_CONFLICT");
  }

  boolean rostered(Long provider, Long account, LocalDate date, String win) {
    return db
        .query(
            StaffRoster.class,
            "from StaffRoster where providerId=?1 and accountId=?2 and startsOn<=?3 and endsOn>?3",
            provider,
            account,
            date)
        .stream()
        .anyMatch(r -> r.visitWindow.equals("DAY") || r.visitWindow.equals(win));
  }

  /** 逐日分派真实待办，班次、跨店负荷和订单版本同一事务核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object assign(Long orderId, Map<String, Object> b) {
    begin();
    var o = db.get(CareOrder.class, orderId);
    allowed(coordinate(o));
    version(o, b);
    Rules.check(
        o.managedCare && Set.of("CONFIRMED", "IN_PROGRESS").contains(o.status), "STATE_INVALID");
    var date = LocalDate.parse(Rules.text(b.get("taskDate"), 10, true));
    Rules.check(date.getYear() >= 2000 && date.getYear() <= 2100, "DATE_INVALID");
    var tasks =
        db.query(CareTask.class, "from CareTask where orderId=?1 and taskDate=?2", orderId, date);
    Rules.check(
        !tasks.isEmpty() && tasks.stream().allMatch(t -> t.status.equals("PENDING")),
        "ASSIGNMENT_LOCKED");
    var rows =
        db.query(
            CareAssignment.class,
            "from CareAssignment where orderId=?1 and taskDate=?2",
            orderId,
            date);
    var a = rows.isEmpty() ? new CareAssignment() : rows.getFirst();
    var previous = a.accountId;
    if (Boolean.TRUE.equals(b.get("remove"))) {
      Rules.check(a.id != null, "NOT_FOUND");
      db.delete(a);
    } else {
      var account = Rules.id(b.get("accountId"));
      var p = db.get(Provider.class, o.providerId);
      Rules.check(member(p, account), "STAFF_INACTIVE");
      String win = o.kind.equals("BOARDING") ? "DAY" : o.visitWindow;
      Rules.check(rostered(o.providerId, account, date, win), "ROSTER_REQUIRED");
      capacity(account, orderId, date, win);
      a.orderId = orderId;
      a.providerId = o.providerId;
      a.accountId = account;
      a.taskDate = date;
      a.visitWindow = win;
      a.departmentId = o.departmentId;
      if (a.id == null) db.save(a);
      else a.version++;
    }
    var e = new CareEntry();
    e.orderId = orderId;
    e.departmentId = o.departmentId;
    e.actorId = access.current().id;
    e.action = "ASSIGN";
    e.note =
        date
            + " / "
            + (Boolean.TRUE.equals(b.get("remove")) ? "UNASSIGNED" : "ACCOUNT #" + a.accountId)
            + " / "
            + Rules.paragraph(b.get("note"), 1000, true);
    e.createdAt = clock.instant();
    db.save(e);
    o.version++;
    access.audit("CARE_ASSIGN", orderId, o.departmentId);
    events.publishEvent(new CareSignal(orderId, "ASSIGN", o.version, previous));
    return Map.of("ok", true);
  }

  /** 开始前检查每个未完成任务日都已有有效人员及班次，停用账号不保留假排期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void coverage(CareOrder o) {
    if (!o.managedCare) return;
    var p = db.get(Provider.class, o.providerId);
    for (var t :
        db.query(CareTask.class, "from CareTask where orderId=?1 and status=?2", o.id, "PENDING")) {
      var rows =
          db.query(
              CareAssignment.class,
              "from CareAssignment where orderId=?1 and taskDate=?2",
              o.id,
              t.taskDate);
      Rules.check(!rows.isEmpty(), "ASSIGNMENT_REQUIRED");
      var a = rows.getFirst();
      Rules.check(
          member(p, a.accountId) && rostered(o.providerId, a.accountId, t.taskDate, a.visitWindow),
          "STAFF_INACTIVE");
      capacity(a.accountId, o.id, t.taskDate, a.visitWindow);
    }
  }

  /** 门店日程最多31天，员工仅看到本人订单；经理可看到本店排期及空缺。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object calendar(Long provider, Map<String, String> filters) {
    access.require("portal");
    var p = db.get(Provider.class, provider);
    var actor = access.current().id;
    allowed(member(p, actor));
    boolean manage = manager(p, actor);
    var from = LocalDate.parse(filters.getOrDefault("from", today().toString()));
    var to = LocalDate.parse(filters.getOrDefault("to", from.plusDays(7).toString()));
    Rules.check(
        from.getYear() >= 2000
            && to.getYear() <= 2100
            && to.isAfter(from)
            && ChronoUnit.DAYS.between(from, to) <= 31,
        "DATE_INVALID");
    var assignments =
        db
            .query(
                CareAssignment.class,
                "from CareAssignment where providerId=?1 and taskDate>=?2 and taskDate<?3",
                provider,
                from,
                to)
            .stream()
            .filter(a -> manage || a.accountId.equals(actor))
            .toList();
    var orders =
        db
            .query(
                CareOrder.class,
                "from CareOrder where providerId=?1 and status in ?2 and (startsOn<?3 and endsOn>?4 or arrivedAt is not null and departedAt is null)",
                provider,
                MarketService.OCCUPY,
                to,
                from)
            .stream()
            .filter(
                o ->
                    manage
                        || o.providerAccountId.equals(actor)
                        || assignments.stream().anyMatch(a -> a.orderId.equals(o.id)))
            .toList();
    var orderIds = orders.stream().map(o -> o.id).collect(java.util.stream.Collectors.toSet());
    var tasks =
        orderIds.isEmpty()
            ? List.<CareTask>of()
            : db
                .query(
                    CareTask.class,
                    "from CareTask where orderId in ?1 and taskDate>=?2 and taskDate<?3",
                    orderIds,
                    from,
                    to)
                .stream()
                .filter(
                    t ->
                        manage
                            || !db.get(CareOrder.class, t.orderId).managedCare
                            || assignments.stream()
                                .anyMatch(
                                    a ->
                                        a.orderId.equals(t.orderId)
                                            && a.taskDate.equals(t.taskDate)))
                .toList();
    var staff = new ArrayList<Object>();
    if (manage) {
      staff.add(
          Map.of(
              "id",
              0,
              "accountId",
              p.accountId,
              "displayName",
              db.get(Account.class, p.accountId).displayName,
              "role",
              "OWNER",
              "status",
              "ACTIVE",
              "version",
              0));
      for (var s : db.query(ShopStaff.class, "from ShopStaff where providerId=?1", provider))
        staff.add(
            Map.of(
                "id",
                s.id,
                "accountId",
                s.accountId,
                "displayName",
                db.get(Account.class, s.accountId).displayName,
                "role",
                s.role,
                "status",
                s.status,
                "version",
                s.version));
    }
    var policies = db.query(ShopPolicy.class, "from ShopPolicy where providerId=?1", provider);
    var out = new LinkedHashMap<String, Object>();
    out.put("providerId", provider);
    out.put("name", p.displayName);
    out.put("ownerAccountId", p.accountId);
    out.put("canManage", manage);
    out.put("today", today());
    out.put(
        "policy",
        policies.isEmpty() ? Map.of("workforceEnabled", false, "version", 0) : policies.getFirst());
    out.put("staff", staff);
    out.put(
        "rosters",
        db
            .query(
                StaffRoster.class,
                "from StaffRoster where providerId=?1 and startsOn<?2 and endsOn>?3",
                provider,
                to,
                from)
            .stream()
            .filter(r -> manage || r.accountId.equals(actor))
            .toList());
    out.put("assignments", assignments);
    out.put("tasks", tasks);
    out.put(
        "orders",
        orders.stream()
            .map(
                o -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("id", o.id);
                  m.put("reference", o.reference);
                  m.put("version", o.version);
                  m.put("kind", o.kind);
                  m.put("status", o.status);
                  m.put("startsOn", o.startsOn);
                  m.put("endsOn", o.endsOn);
                  m.put("visitWindow", o.visitWindow);
                  m.put("managedCare", o.managedCare);
                  m.put("stayStatus", o.stayStatus);
                  m.put(
                      "overdue",
                      o.arrivedAt != null && o.departedAt == null && !today().isBefore(o.endsOn));
                  return m;
                })
            .toList());
    return out;
  }
}
