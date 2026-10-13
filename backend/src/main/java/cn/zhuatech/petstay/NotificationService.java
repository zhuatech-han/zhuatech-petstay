// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 业务同事务站内消息、邮箱验证和有界出站队列，事件去重不伪造送达。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class NotificationService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final WorkforceService workforce;
  final Clock clock;
  final EmailDelivery mail;
  static final Set<String> QUEUE = Set.of("QUEUED", "RETRY", "SENDING");
  static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("CONFIRM", "订单确认 / Booking confirmed"),
          Map.entry("ASSIGN", "照护排期调整 / Care assignment changed"),
          Map.entry("START", "照护开始 / Care started"),
          Map.entry("CHECK_OUT", "宠物已接回 / Pet collected"),
          Map.entry("FINISH", "请核对交接 / Handover ready"),
          Map.entry("ACCEPT", "主人确认完成 / Owner confirmed"),
          Map.entry("DISPUTE", "交接存在争议 / Handover disputed"),
          Map.entry("RESPOND", "照护方已回复 / Provider responded"),
          Map.entry("CANCEL", "订单取消 / Booking cancelled"),
          Map.entry("DUE", "今日照护待办 / Care due today"),
          Map.entry("UNASSIGNED", "尚未安排人员 / Staff assignment needed"),
          Map.entry("OVERDUE", "超期未接回 / Collection overdue"),
          Map.entry("TOMORROW", "明日预约 / Booking tomorrow"));

  /** 队列共用数据库与时钟，不依赖内存消息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public NotificationService(
      Store db,
      AccessService access,
      AdminService admin,
      WorkforceService workforce,
      Clock clock,
      EmailDelivery mail) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.workforce = workforce;
    this.clock = clock;
    this.mail = mail;
  }

  void begin() {
    admin.lock();
    access.require("portal");
  }

  NotificationPreference pref(Long account) {
    var rows =
        db.query(
            NotificationPreference.class,
            "from NotificationPreference where accountId=?1",
            account);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  NotificationPreference own() {
    Long account = access.current().id;
    var p = pref(account);
    if (p == null) {
      p = new NotificationPreference();
      p.accountId = account;
      p.departmentId = access.current().departmentId;
      db.save(p);
    }
    return p;
  }

  Map<String, Object> view(NotificationPreference p) {
    return Map.of(
        "zone",
        db.get(Department.class, 1L).zone,
        "version",
        p == null ? 0 : p.version,
        "email",
        p == null ? "" : p.email,
        "emailEnabled",
        p != null && p.emailEnabled,
        "remindersEnabled",
        p == null || p.remindersEnabled,
        "reminderHour",
        p == null ? 9 : p.reminderHour,
        "language",
        p == null ? "zh" : p.language,
        "mailAvailable",
        mail.available(),
        "pendingVerification",
        p != null && !p.pendingEmail.isBlank());
  }

  /** 只返回本人偏好和邮件可用状态，不返回SMTP凭据或验证码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object preferences() {
    access.require("portal");
    return view(pref(access.current().id));
  }

  /** 本人主动启用已验证邮箱，提醒时间为平台时区整点；关闭渠道取消旧队列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object savePreferences(Map<String, Object> b) {
    begin();
    var existing = pref(access.current().id);
    if (existing != null)
      Rules.check(existing.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
    var p = own();
    boolean enabled = Rules.flag(b.get("emailEnabled"));
    Rules.check(!enabled || (mail.available() && !p.email.isBlank()), "EMAIL_NOT_VERIFIED");
    p.emailEnabled = enabled;
    p.remindersEnabled = Rules.flag(b.get("remindersEnabled"));
    p.reminderHour = Rules.integer(b.get("reminderHour"), 0, 23);
    p.language = Rules.text(b.get("language"), 10, true);
    Rules.check(Set.of("zh", "en").contains(p.language), "INVALID_INPUT");
    p.version++;
    access.audit("NOTIFICATION_PREFERENCE", p.id, p.departmentId);
    return view(p);
  }

  static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HASH_FAILED");
    }
  }

  /** 验证邮件限每账号每小时三次、间隔60秒，验证码只存散列且15分钟失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object requestEmail(Map<String, Object> b) {
    begin();
    Rules.check(mail.available(), "MAIL_UNAVAILABLE");
    var p = own();
    String email = Rules.text(b.get("email"), 254, true).toLowerCase(Locale.ROOT);
    Rules.check(
        email.matches(
                "[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9.-]{0,185})\\.[a-z]{2,24}")
            && !email.contains(".."),
        "EMAIL_INVALID");
    Rules.check(
        p.codeRequestedAt == null || !p.codeRequestedAt.plusSeconds(60).isAfter(clock.instant()),
        "EMAIL_RATE_LIMIT");
    Rules.check(
        db.count(
                "select count(n) from CareNotification n where n.accountId=?1 and n.event=?2 and n.createdAt>?3",
                p.accountId,
                "EMAIL_VERIFY",
                clock.instant().minusSeconds(3600))
            < 3,
        "EMAIL_RATE_LIMIT");
    String code = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000));
    try {
      mail.send(
          email,
          "PetStay 邮箱验证 / Email verification",
          "PetStay verification code: "
              + code
              + "\n有效15分钟 / Valid for 15 minutes. If you did not request this, ignore it.");
    } catch (Exception e) {
      throw new Problem(503, "MAIL_FAILED");
    }
    p.pendingEmail = email;
    p.codeHash = hash(code);
    p.codeExpiresAt = clock.instant().plusSeconds(900);
    p.codeRequestedAt = clock.instant();
    p.codeAttempts = 0;
    p.version++;
    var n = new CareNotification();
    n.accountId = p.accountId;
    n.departmentId = p.departmentId;
    n.event = "EMAIL_VERIFY";
    n.eventKey = "VERIFY:" + UUID.randomUUID();
    n.createdAt = clock.instant();
    n.nextAttemptAt = n.createdAt;
    n.deliveryStatus = "SENT";
    n.sentAt = n.createdAt;
    db.save(n);
    access.audit("EMAIL_VERIFICATION_REQUEST", p.id, p.departmentId);
    return view(p);
  }

  /** 有界验证失败次数须提交，成功后关闭邮件偏好，需本人另行开启。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(noRollbackFor = Problem.class)
  public Object verifyEmail(Map<String, Object> b) {
    begin();
    var p = own();
    String code = Rules.text(b.get("code"), 6, true);
    Rules.check(
        !p.codeHash.isEmpty() && p.codeExpiresAt.isAfter(clock.instant()) && p.codeAttempts < 5,
        "EMAIL_CODE_EXPIRED");
    p.codeAttempts++;
    p.version++;
    Rules.check(
        code.matches("[0-9]{6}")
            && MessageDigest.isEqual(
                hash(code).getBytes(StandardCharsets.UTF_8),
                p.codeHash.getBytes(StandardCharsets.UTF_8)),
        "EMAIL_CODE_INVALID");
    p.email = p.pendingEmail;
    p.pendingEmail = "";
    p.codeHash = "";
    p.emailEnabled = false;
    access.audit("EMAIL_VERIFIED", p.id, p.departmentId);
    return view(p);
  }

  Set<Long> recipients(CareOrder o) {
    var ids = new LinkedHashSet<Long>();
    ids.add(o.ownerId);
    ids.add(o.providerAccountId);
    for (var s :
        db.query(
            ShopStaff.class,
            "from ShopStaff where providerId=?1 and status=?2 and role=?3",
            o.providerId,
            "ACTIVE",
            "MANAGER")) ids.add(s.accountId);
    for (var a : db.query(CareAssignment.class, "from CareAssignment where orderId=?1", o.id))
      ids.add(a.accountId);
    return ids;
  }

  boolean eligible(CareOrder o, Long account) {
    return workforce.activeAccount(account)
        && (o.ownerId.equals(account)
            || o.providerAccountId.equals(account)
            || workforce.visible(o, account));
  }

  void message(CareOrder o, Long account, String event, String key, boolean reminder) {
    if (!eligible(o, account)) return;
    var p = pref(account);
    if (reminder
        && p != null
        && (!p.remindersEnabled
            || LocalTime.now(clock.withZone(ZoneId.of(db.get(Department.class, 1L).zone))).getHour()
                < p.reminderHour)) return;
    if (reminder
        && p == null
        && LocalTime.now(clock.withZone(ZoneId.of(db.get(Department.class, 1L).zone))).getHour()
            < 9) return;
    if (db.count(
            "select count(n) from CareNotification n where n.accountId=?1 and n.eventKey=?2",
            account,
            key)
        > 0) return;
    var n = new CareNotification();
    n.accountId = account;
    n.orderId = o.id;
    n.departmentId = o.departmentId;
    n.reference = o.reference;
    n.event = event;
    n.eventKey = key;
    n.createdAt = clock.instant();
    n.nextAttemptAt = n.createdAt;
    if (p != null && p.emailEnabled && !p.email.isBlank()) {
      n.destination = p.email;
      n.deliveryStatus = "QUEUED";
      n.language = p.language;
    }
    db.save(n);
  }

  /** 订单双方与授权员工收到同事务消息，正文不复制私有业务资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @EventListener
  public void onSignal(CareSignal signal) {
    var o = db.get(CareOrder.class, signal.orderId());
    var recipients = recipients(o);
    if (signal.extraAccountId() != null) recipients.add(signal.extraAccountId());
    for (Long account : recipients)
      message(
          o, account, signal.event(), o.id + ":" + signal.event() + ":" + signal.version(), false);
  }

  /** 平台运营日期每日提醒去重，补充超期实际在养任务，不按原价擅自加收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void generateReminders() {
    admin.lock();
    var today = workforce.today();
    for (var o :
        db.query(CareOrder.class, "from CareOrder where status in ?1", MarketService.OCCUPY)) {
      if (o.arrivedAt != null && o.departedAt == null && !today.isBefore(o.endsOn)) {
        LocalDate from = o.endsOn;
        var existing = db.query(CareTask.class, "from CareTask where orderId=?1", o.id);
        var last =
            existing.stream()
                .map(t -> t.taskDate)
                .max(LocalDate::compareTo)
                .orElse(from.minusDays(1));
        from = last.plusDays(1);
        int days = 0;
        for (var d = from; !d.isAfter(today) && days < 366; d = d.plusDays(1), days++)
          for (String kind : List.of("FEED", "ACTIVITY", "CLEAN")) {
            var t = new CareTask();
            t.orderId = o.id;
            t.departmentId = o.departmentId;
            t.taskDate = d;
            t.kind = kind;
            t.status = "PENDING";
            db.save(t);
          }
        if (days > 0) o.version++;
        for (Long account : recipients(o))
          message(o, account, "OVERDUE", o.id + ":OVERDUE:" + today, true);
      }
      if (o.status.equals("CONFIRMED") && o.startsOn.equals(today.plusDays(1)))
        for (Long account : recipients(o))
          message(o, account, "TOMORROW", o.id + ":TOMORROW:" + today, true);
      var pending =
          db.query(
              CareTask.class,
              "from CareTask where orderId=?1 and taskDate<=?2 and status=?3",
              o.id,
              today,
              "PENDING");
      if (!pending.isEmpty() && Set.of("CONFIRMED", "IN_PROGRESS").contains(o.status)) {
        for (Long account : recipients(o)) message(o, account, "DUE", o.id + ":DUE:" + today, true);
        if (o.managedCare
            && pending.stream()
                .anyMatch(
                    t ->
                        db.count(
                                "select count(a) from CareAssignment a where a.orderId=?1 and a.taskDate=?2",
                                o.id,
                                t.taskDate)
                            == 0)) {
          message(o, o.providerAccountId, "UNASSIGNED", o.id + ":UNASSIGNED:" + today, true);
          for (var s :
              db.query(
                  ShopStaff.class,
                  "from ShopStaff where providerId=?1 and status=?2 and role=?3",
                  o.providerId,
                  "ACTIVE",
                  "MANAGER"))
            message(o, s.accountId, "UNASSIGNED", o.id + ":UNASSIGNED:" + today, true);
        }
      }
    }
  }

  /** 本人站内记录数据库分页，状态只代表SMTP接受或失败，不代表读信。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object inbox(Map<String, String> filters) {
    access.require("portal");
    Long id = access.current().id;
    int page = Rules.integer(filters.getOrDefault("page", "1"), 1, 200000);
    long total =
        db.count(
            "select count(n) from CareNotification n where n.accountId=?1 and n.event<>?2",
            id,
            "EMAIL_VERIFY");
    int pages = (int) Math.max(1, (total + 19) / 20);
    page = Math.min(page, pages);
    return Map.of(
        "items",
        db.page(
            CareNotification.class,
            "from CareNotification where accountId=?1 and event<>?2 order by id desc",
            (page - 1) * 20,
            20,
            id,
            "EMAIL_VERIFY"),
        "page",
        page,
        "pages",
        pages,
        "total",
        total,
        "unread",
        db.count(
            "select count(n) from CareNotification n where n.accountId=?1 and n.readAt is null and n.event<>?2",
            id,
            "EMAIL_VERIFY"));
  }

  /** 本人标记已读或重试最终失败消息，最多五次手动重试，不重发已成功记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object action(Long id, String action, Map<String, Object> b) {
    begin();
    var n = db.get(CareNotification.class, id);
    workforce.allowed(n.accountId.equals(access.current().id));
    workforce.version(n, b);
    switch (action) {
      case "read" -> n.readAt = clock.instant();
      case "retry" -> {
        Rules.check(
            n.deliveryStatus.equals("FAILED") && n.attempts < 10 && sendAllowed(n),
            "NOTIFICATION_RETRY_DENIED");
        n.deliveryStatus = "QUEUED";
        n.nextAttemptAt = clock.instant();
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    n.version++;
    return Map.of("ok", true);
  }

  boolean sendAllowed(CareNotification n) {
    var p = pref(n.accountId);
    return mail.available()
        && p != null
        && p.emailEnabled
        && p.email.equals(n.destination)
        && (!Set.of("DUE", "UNASSIGNED", "OVERDUE", "TOMORROW").contains(n.event)
            || p.remindersEnabled)
        && n.orderId != null
        && eligible(db.get(CareOrder.class, n.orderId), n.accountId);
  }

  /** 提交租约后才发邮件，网络等待不持有业务写锁；崩溃租约可重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Delivery claim() {
    admin.lock();
    var rows =
        db.page(
            CareNotification.class,
            "from CareNotification where deliveryStatus in ?1 and nextAttemptAt<=?2 order by id",
            0,
            1,
            QUEUE,
            clock.instant());
    if (rows.isEmpty()) return null;
    var n = rows.getFirst();
    if (!sendAllowed(n)) {
      n.deliveryStatus = "CANCELLED";
      n.failureCode = "PREFERENCE_OR_ACCESS_CHANGED";
      n.version++;
      return new Delivery(n.id, "", "", "", "", true);
    }
    if (n.attempts >= 10) {
      n.deliveryStatus = "FAILED";
      n.failureCode = "ATTEMPTS_EXHAUSTED";
      n.version++;
      return new Delivery(n.id, "", "", "", "", true);
    }
    n.attempts++;
    n.deliveryStatus = "SENDING";
    n.nextAttemptAt = clock.instant().plusSeconds(120);
    n.version++;
    return new Delivery(n.id, n.destination, n.reference, n.event, n.language, false);
  }

  /** 保存安全的错误分类，不保存SMTP异常正文或凭据；自动重试最多五次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void finish(Long id, boolean success) {
    admin.lock();
    var n = db.get(CareNotification.class, id);
    if (!n.deliveryStatus.equals("SENDING")) return;
    n.deliveryStatus = success ? "SENT" : n.attempts < 5 ? "RETRY" : "FAILED";
    n.failureCode = success ? "" : "SMTP_FAILURE";
    if (success) n.sentAt = clock.instant();
    else
      n.nextAttemptAt =
          clock.instant().plusSeconds(Math.min(3600, 60L * (1L << Math.min(n.attempts, 6))));
    n.version++;
  }

  /** 出站任务快照仅在内存传递，不把凭据带进消息或接口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Delivery(
      Long id,
      String destination,
      String reference,
      String event,
      String language,
      boolean skipped) {}
}
