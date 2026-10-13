// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 真实匹配、容量和指定双方订单事务；公共结果不含联系人或准确地址。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MarketService {
  static final Set<String> OCCUPY = Set.of("CONFIRMED", "IN_PROGRESS", "REVIEW", "DISPUTED");
  static final Set<String> KINDS = Set.of("BOARDING", "HOME_VISIT");
  final Store db;
  final AccessService access;
  final AdminService admin;
  final WorkforceService workforce;
  final org.springframework.context.ApplicationEventPublisher events;
  final Clock clock;
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  /** 连接实时身份、锁、数据库和可测试时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public MarketService(
      Store db,
      AccessService access,
      AdminService admin,
      Clock clock,
      WorkforceService workforce,
      org.springframework.context.ApplicationEventPublisher events) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
    this.workforce = workforce;
    this.events = events;
  }

  void begin(String permission) {
    admin.lock();
    access.require(permission);
  }

  void allowed(boolean ok) {
    if (!ok) throw new Problem(403, "OUT_OF_SCOPE");
  }

  void version(RecordBase r, Map<String, Object> b) {
    Rules.check(r.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of(db.get(Department.class, 1L).zone)));
  }

  LocalDate date(Object v) {
    var d = LocalDate.parse(Rules.text(v, 10, true));
    Rules.check(d.getYear() >= 2000 && d.getYear() <= 2100, "DATE_INVALID");
    return d;
  }

  String txt(Map<String, Object> b, String k, int max) {
    return Rules.text(b.get(k), max, true);
  }

  String note(Map<String, Object> b) {
    return Rules.paragraph(b.get("note"), 1800, true);
  }

  BigDecimal weight(Object v) {
    try {
      var n =
          new BigDecimal(Objects.toString(v, "")).setScale(2, java.math.RoundingMode.UNNECESSARY);
      Rules.check(n.signum() > 0 && n.compareTo(new BigDecimal("200")) <= 0, "WEIGHT_INVALID");
      return n;
    } catch (NumberFormatException | ArithmeticException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  String window(String kind, Object value) {
    if (kind.equals("BOARDING")) return "NIGHT";
    var s = Rules.text(value, 20, true);
    Rules.check(Set.of("AM", "PM", "EVENING").contains(s), "WINDOW_INVALID");
    return s;
  }

  void dates(LocalDate from, LocalDate to, int max) {
    Rules.check(
        !from.isBefore(today()) && to.isAfter(from) && ChronoUnit.DAYS.between(from, to) <= max,
        "DATE_INVALID");
  }

  boolean overlap(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return a.isBefore(d) && c.isBefore(b);
  }

  boolean activeAccount(Long id) {
    var a = db.get(Account.class, id);
    return a.enabled
        && db.get(Department.class, a.departmentId).enabled
        && db.get(AccessRole.class, a.roleId).permissions.contains("portal");
  }

  boolean speciesEnabled(String s) {
    return Set.of("DOG", "CAT").contains(s)
        && db
            .query(
                DictionaryEntry.class,
                "from DictionaryEntry where type=?1 and code=?2",
                "SPECIES",
                s)
            .stream()
            .anyMatch(d -> d.enabled);
  }

  void limit(Class<?> type, String code) {
    long active =
        type == CareOrder.class
            ? db.count("select count(o) from CareOrder o where o.status in ?1", OCCUPY)
            : db.count(
                "select count(r) from CareRequest r where r.status in ?1", Set.of("DRAFT", "OPEN"));
    Rules.check(active < admin.setting(code), "RESOURCE_LIMIT");
  }

  Member member() {
    access.require("portal");
    var id = access.current().id;
    var rows = db.query(Member.class, "from Member where accountId=?1", id);
    if (!rows.isEmpty()) return rows.getFirst();
    throw new Problem(409, "PROFILE_UNAVAILABLE");
  }

  Member memberOf(Long id) {
    var rows = db.query(Member.class, "from Member where accountId=?1", id);
    Rules.check(!rows.isEmpty(), "CONTACT_REQUIRED");
    return rows.getFirst();
  }

  Member readyMember() {
    var m = member();
    Rules.check(
        !m.contact.isBlank()
            && !m.contactName.isBlank()
            && !m.city.isBlank()
            && !m.district.isBlank(),
        "CONTACT_REQUIRED");
    return m;
  }

  Pet ownPet(Long id) {
    var p = db.get(Pet.class, id);
    allowed(p.ownerId.equals(access.current().id));
    return p;
  }

  CareRequest ownRequest(Long id) {
    var r = db.get(CareRequest.class, id);
    allowed(r.ownerId.equals(access.current().id));
    return r;
  }

  Provider ownProvider() {
    access.require("portal");
    var rows = db.query(Provider.class, "from Provider where accountId=?1", access.current().id);
    Rules.check(!rows.isEmpty(), "PROVIDER_REQUIRED");
    return rows.getFirst();
  }

  boolean operation(Long dept) {
    return access.has("moderate")
        && !access.role().scope.equals("ASSIGNED")
        && access.department(dept);
  }

  boolean visible(CareOrder o) {
    var id = access.current().id;
    return id.equals(o.ownerId)
        || id.equals(o.providerAccountId)
        || operation(o.departmentId)
        || (access.has("portal") && workforce.visible(o, id));
  }

  CareOrder order(Long id) {
    var o = db.get(CareOrder.class, id);
    allowed(visible(o));
    return o;
  }

  List<CareOrder> reservations(Long provider) {
    return db.query(
        CareOrder.class, "from CareOrder where providerId=?1 and status in ?2", provider, OCCUPY);
  }

  boolean published(Provider p) {
    return p.status.equals("APPROVED") && activeAccount(p.accountId);
  }

  boolean fits(Provider p, CareRequest r) {
    var pet = db.get(Pet.class, r.petId);
    return published(p)
        && !p.accountId.equals(r.ownerId)
        && p.city.equals(r.city)
        && p.district.equals(r.district)
        && pet.enabled
        && speciesEnabled(pet.species)
        && (p.species.equals("BOTH") || p.species.equals(pet.species))
        && p.maxWeightKg.compareTo(pet.weightKg) >= 0
        && (r.kind.equals("BOARDING") ? p.boarding : p.homeVisit);
  }

  boolean available(Provider p, String kind, String win, LocalDate from, LocalDate to) {
    var windows =
        db.query(
            Availability.class,
            "from Availability where providerId=?1 and kind=?2 and visitWindow=?3",
            p.id,
            kind,
            win);
    var booked = reservations(p.id);
    int cap = kind.equals("BOARDING") ? p.boardingCapacity : p.visitCapacity;
    for (var d = from; d.isBefore(to); d = d.plusDays(1)) {
      final var day = d;
      if (windows.stream().noneMatch(w -> !day.isBefore(w.startsOn) && day.isBefore(w.endsOn)))
        return false;
      if (booked.stream()
              .filter(
                  o ->
                      o.kind.equals(kind)
                          && o.visitWindow.equals(win)
                          && !day.isBefore(o.startsOn)
                          && (day.isBefore(o.endsOn)
                              || (o.arrivedAt != null && o.departedAt == null))
                          && !(o.kind.equals("BOARDING") && o.departedAt != null))
              .count()
          >= cap) return false;
    }
    return true;
  }

  boolean petFree(CareRequest r) {
    return db.query(CareOrder.class, "from CareOrder where petId=?1", r.petId).stream()
        .noneMatch(
            o ->
                OCCUPY.contains(o.status)
                    && !(o.kind.equals("BOARDING") && o.departedAt != null)
                    && (overlap(o.startsOn, o.endsOn, r.startsOn, r.endsOn)
                        || (o.arrivedAt != null
                            && o.departedAt == null
                            && r.endsOn.isAfter(o.startsOn)))
                    && (o.kind.equals("BOARDING")
                        || r.kind.equals("BOARDING")
                        || o.visitWindow.equals(r.visitWindow)));
  }

  Map<String, Object> providerCard(Provider p) {
    var reviews =
        db.query(Review.class, "from Review where providerId=?1 and status=?2", p.id, "APPROVED");
    return map(
        "id",
        p.id,
        "displayName",
        p.displayName,
        "city",
        p.city,
        "district",
        p.district,
        "intro",
        p.intro,
        "species",
        p.species,
        "maxWeightKg",
        p.maxWeightKg,
        "boarding",
        p.boarding,
        "homeVisit",
        p.homeVisit,
        "boardingPriceMinor",
        p.boardingPriceMinor,
        "visitPriceMinor",
        p.visitPriceMinor,
        "currency",
        "CNY",
        "reviewCount",
        reviews.size(),
        "rating",
        reviews.stream().mapToInt(x -> x.rating).average().orElse(0));
  }

  Map<String, Object> publicRequest(CareRequest r) {
    var p = db.get(Pet.class, r.petId);
    return map(
        "id",
        r.id,
        "version",
        r.version,
        "title",
        r.title,
        "publicNote",
        r.publicNote,
        "kind",
        r.kind,
        "city",
        r.city,
        "district",
        r.district,
        "species",
        p.species,
        "weightKg",
        p.weightKg,
        "startsOn",
        r.startsOn,
        "endsOn",
        r.endsOn,
        "visitWindow",
        r.visitWindow,
        "status",
        r.status);
  }

  Map<String, Object> offerView(Offer o) {
    var p = db.get(Provider.class, o.providerId);
    return map(
        "id",
        o.id,
        "version",
        o.version,
        "requestId",
        o.requestId,
        "providerId",
        o.providerId,
        "providerName",
        p.displayName,
        "status",
        o.status,
        "unitMinor",
        o.unitMinor,
        "units",
        o.units,
        "totalMinor",
        o.totalMinor,
        "note",
        o.note,
        "expiresAt",
        o.expiresAt);
  }

  Map<String, Object> orderSummary(CareOrder o) {
    return map(
        "id",
        o.id,
        "version",
        o.version,
        "reference",
        o.reference,
        "requestId",
        o.requestId,
        "ownerId",
        o.ownerId,
        "providerAccountId",
        o.providerAccountId,
        "kind",
        o.kind,
        "startsOn",
        o.startsOn,
        "endsOn",
        o.endsOn,
        "visitWindow",
        o.visitWindow,
        "status",
        o.status,
        "totalMinor",
        o.totalMinor,
        "createdAt",
        o.createdAt,
        "providerId",
        o.providerId,
        "managedCare",
        o.managedCare,
        "stayStatus",
        o.stayStatus,
        "arrivedAt",
        o.arrivedAt,
        "departedAt",
        o.departedAt);
  }

  Map<String, Object> photoMeta(CarePhoto p) {
    return map(
        "id",
        p.id,
        "orderId",
        p.orderId,
        "caption",
        p.caption,
        "mime",
        p.mime,
        "sha256",
        p.sha256,
        "createdAt",
        p.createdAt,
        "url",
        "/api/orders/" + p.orderId + "/photos/" + p.id);
  }

  Map<String, Object> map(Object... pairs) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i].toString(), pairs[i + 1]);
    return m;
  }

  /** 匿名服务目录仅显示已审核公开资料；可按真实可用档期筛选。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object catalog(Map<String, String> filters) {
    var rows = db.all(Provider.class).stream().filter(this::published);
    for (var key : List.of("city", "district", "species")) {
      var v = filters.get(key);
      if (v != null && !v.isBlank())
        rows =
            rows.filter(
                p ->
                    switch (key) {
                      case "city" -> p.city.equals(v);
                      case "district" -> p.district.equals(v);
                      default -> p.species.equals("BOTH") || p.species.equals(v);
                    });
    }
    var k = filters.get("kind");
    if (k != null && !k.isBlank()) {
      Rules.check(KINDS.contains(k), "INVALID_INPUT");
      rows = rows.filter(p -> k.equals("BOARDING") ? p.boarding : p.homeVisit);
      if (filters.containsKey("startsOn") && filters.containsKey("endsOn")) {
        var f = date(filters.get("startsOn"));
        var t = date(filters.get("endsOn"));
        dates(f, t, k.equals("BOARDING") ? 30 : 14);
        var w = window(k, filters.get("visitWindow"));
        rows = rows.filter(p -> available(p, k, w, f, t));
      }
    }
    return rows.sorted(Comparator.comparing(p -> p.id)).map(this::providerCard).toList();
  }

  /** 公共评价不公开账号、订单、联系资料，只有审核过的评价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object publicReviews(Long id) {
    var p = db.get(Provider.class, id);
    Rules.check(published(p), "NOT_AVAILABLE");
    return db
        .query(Review.class, "from Review where providerId=?1 and status=?2", id, "APPROVED")
        .stream()
        .map(
            r ->
                map(
                    "rating",
                    r.rating,
                    "comment",
                    r.comment,
                    "createdAt",
                    r.createdAt,
                    "author",
                    "宠物主 / Pet owner"))
        .toList();
  }

  /** 本人资料及目录读取；候选需求隐去准确地址、联系人和私有照护说明。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object read(String kind) {
    var id = access.current().id;
    if (kind.equals("orders")) {
      allowed(access.has("portal") || operation(access.current().departmentId));
      return db.all(CareOrder.class).stream()
          .filter(this::visible)
          .map(this::orderSummary)
          .toList();
    }
    access.require("portal");
    return switch (kind) {
      case "profile" -> member();
      case "pets" -> db.query(Pet.class, "from Pet where ownerId=?1", id);
      case "requests" -> db.query(CareRequest.class, "from CareRequest where ownerId=?1", id);
      case "orders" ->
          db.all(CareOrder.class).stream().filter(this::visible).map(this::orderSummary).toList();
      case "provider" -> {
        var ps = db.query(Provider.class, "from Provider where accountId=?1", id);
        yield ps.isEmpty()
            ? map("provider", null, "availability", List.of())
            : map(
                "provider",
                ps.getFirst(),
                "availability",
                db.query(
                    Availability.class,
                    "from Availability where providerId=?1 order by id",
                    ps.getFirst().id));
      }
      case "opportunities" -> {
        var p = ownProvider();
        yield db.query(CareRequest.class, "from CareRequest where status=?1", "OPEN").stream()
            .filter(
                r ->
                    !r.startsOn.isBefore(today())
                        && fits(p, r)
                        && available(p, r.kind, r.visitWindow, r.startsOn, r.endsOn))
            .map(this::publicRequest)
            .toList();
      }
      case "my-offers" -> {
        var p = ownProvider();
        yield db.query(Offer.class, "from Offer where providerId=?1", p.id).stream()
            .map(this::offerView)
            .toList();
      }
      case "options" ->
          map(
              "species",
              db
                  .query(DictionaryEntry.class, "from DictionaryEntry where type=?1", "SPECIES")
                  .stream()
                  .filter(d -> d.enabled && Set.of("CAT", "DOG").contains(d.code))
                  .toList(),
              "today",
              today(),
              "zone",
              db.get(Department.class, 1L).zone);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 宠物主本人更新联系方式；旧订单使用冻结资料不随档案被覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveProfile(Map<String, Object> b) {
    begin("portal");
    var m = member();
    version(m, b);
    m.contactName = txt(b, "contactName", 120);
    m.contact = txt(b, "contact", 160);
    m.city = txt(b, "city", 80);
    m.district = txt(b, "district", 80);
    m.version++;
    access.audit("PROFILE_SAVE", m.id, m.departmentId);
    return m;
  }

  /** 建立或维护本人宠物；有需求历史的物种身份不能改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object savePet(Long id, Map<String, Object> b) {
    begin("portal");
    member();
    var p = id == null ? new Pet() : ownPet(id);
    if (id != null) version(p, b);
    String s = txt(b, "species", 60);
    Rules.check(speciesEnabled(s), "SPECIES_DISABLED");
    if (id != null && !p.species.equals(s))
      Rules.check(
          db.query(CareRequest.class, "from CareRequest where petId=?1", id).isEmpty(),
          "IDENTITY_LOCKED");
    p.ownerId = access.current().id;
    p.departmentId = access.current().departmentId;
    p.name = txt(b, "name", 120);
    p.species = s;
    p.weightKg = weight(b.get("weightKg"));
    p.careNotes = Rules.paragraph(b.get("careNotes"), 1800, false);
    p.enabled = Rules.flag(b.get("enabled"));
    if (id == null) db.save(p);
    else p.version++;
    access.audit("PET_SAVE", p.id, p.departmentId);
    return p;
  }

  /** 未被使用误建宠物可删除；有需求和订单的身份保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deletePet(Long id, Map<String, Object> b) {
    begin("portal");
    var p = ownPet(id);
    version(p, b);
    Rules.check(
        db.query(CareRequest.class, "from CareRequest where petId=?1", id).isEmpty(),
        "HISTORY_LOCKED");
    access.audit("PET_DELETE", id, p.departmentId);
    db.delete(p);
    return Map.of("ok", true);
  }

  /** 照护方资料修改重新进入审核；履约中不修改影响容量的整份资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveProvider(Map<String, Object> b) {
    begin("portal");
    readyMember();
    var rows = db.query(Provider.class, "from Provider where accountId=?1", access.current().id);
    var p = rows.isEmpty() ? new Provider() : rows.getFirst();
    if (p.id != null) {
      version(p, b);
      Rules.check(reservations(p.id).isEmpty(), "ACTIVE_ORDERS");
    }
    p.accountId = access.current().id;
    p.departmentId = access.current().departmentId;
    p.displayName = txt(b, "displayName", 120);
    p.city = txt(b, "city", 80);
    p.district = txt(b, "district", 80);
    p.address = Rules.text(b.get("address"), 500, false);
    p.intro = Rules.paragraph(b.get("intro"), 1200, true);
    p.species = txt(b, "species", 20);
    Rules.check(Set.of("DOG", "CAT", "BOTH").contains(p.species), "INVALID_INPUT");
    p.maxWeightKg = weight(b.get("maxWeightKg"));
    p.boarding = Rules.flag(b.get("boarding"));
    p.homeVisit = Rules.flag(b.get("homeVisit"));
    Rules.check(p.boarding || p.homeVisit, "SERVICE_REQUIRED");
    if (p.boarding) Rules.check(!p.address.isBlank(), "ADDRESS_REQUIRED");
    p.boardingCapacity = Rules.integer(b.get("boardingCapacity"), 1, 20);
    p.visitCapacity = Rules.integer(b.get("visitCapacity"), 1, 10);
    p.boardingPriceMinor = Rules.integer(b.get("boardingPriceMinor"), 0, 1000000);
    p.visitPriceMinor = Rules.integer(b.get("visitPriceMinor"), 0, 1000000);
    p.status = "PENDING";
    p.reviewNote = "";
    p.updatedAt = clock.instant();
    if (p.id == null) db.save(p);
    else p.version++;
    access.audit("PROVIDER_SUBMIT", p.id, p.departmentId);
    return p;
  }

  /** 公布真实连续档期；同类型和窗口不重叠，不移除正在占用的档期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveAvailability(Long id, Map<String, Object> b) {
    begin("portal");
    var p = ownProvider();
    var w = id == null ? new Availability() : db.get(Availability.class, id);
    if (id != null) {
      allowed(w.providerId.equals(p.id));
      version(w, b);
      Rules.check(
          reservations(p.id).stream()
              .noneMatch(
                  o ->
                      o.kind.equals(w.kind)
                          && o.visitWindow.equals(w.visitWindow)
                          && overlap(o.startsOn, o.endsOn, w.startsOn, w.endsOn)),
          "ACTIVE_ORDERS");
    }
    var kind = txt(b, "kind", 20);
    Rules.check(KINDS.contains(kind), "INVALID_INPUT");
    Rules.check(kind.equals("BOARDING") ? p.boarding : p.homeVisit, "SERVICE_DISABLED");
    var win = window(kind, b.get("visitWindow"));
    var f = date(b.get("startsOn"));
    var t = date(b.get("endsOn"));
    dates(f, t, 90);
    Rules.check(
        db
            .query(
                Availability.class,
                "from Availability where providerId=?1 and kind=?2 and visitWindow=?3",
                p.id,
                kind,
                win)
            .stream()
            .noneMatch(x -> !Objects.equals(x.id, id) && overlap(f, t, x.startsOn, x.endsOn)),
        "WINDOW_CONFLICT");
    w.providerId = p.id;
    w.departmentId = p.departmentId;
    w.kind = kind;
    w.visitWindow = win;
    w.startsOn = f;
    w.endsOn = t;
    if (id == null) db.save(w);
    else w.version++;
    access.audit("AVAILABILITY_SAVE", w.id, w.departmentId);
    return w;
  }

  /** 删除未被订单占用的档期；未选报价选定时会重新校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deleteAvailability(Long id, Map<String, Object> b) {
    begin("portal");
    var p = ownProvider();
    var w = db.get(Availability.class, id);
    allowed(w.providerId.equals(p.id));
    version(w, b);
    Rules.check(
        reservations(p.id).stream()
            .noneMatch(
                o ->
                    o.kind.equals(w.kind)
                        && o.visitWindow.equals(w.visitWindow)
                        && overlap(o.startsOn, o.endsOn, w.startsOn, w.endsOn)),
        "ACTIVE_ORDERS");
    access.audit("AVAILABILITY_DELETE", id, w.departmentId);
    db.delete(w);
    return Map.of("ok", true);
  }

  /** 需求草稿关联本人宠物，公开说明和私有地址分开保存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveRequest(Long id, Map<String, Object> b) {
    begin("portal");
    readyMember();
    var r = id == null ? new CareRequest() : ownRequest(id);
    if (id != null) {
      version(r, b);
      Rules.check(r.status.equals("DRAFT"), "STATE_INVALID");
    } else limit(CareRequest.class, "max_requests");
    var p = ownPet(Rules.id(b.get("petId")));
    Rules.check(p.enabled && speciesEnabled(p.species), "PET_DISABLED");
    r.kind = txt(b, "kind", 20);
    Rules.check(KINDS.contains(r.kind), "INVALID_INPUT");
    r.startsOn = date(b.get("startsOn"));
    r.endsOn = date(b.get("endsOn"));
    dates(r.startsOn, r.endsOn, r.kind.equals("BOARDING") ? 30 : 14);
    r.visitWindow = window(r.kind, b.get("visitWindow"));
    r.city = txt(b, "city", 80);
    r.district = txt(b, "district", 80);
    r.title = txt(b, "title", 120);
    r.publicNote = Rules.paragraph(b.get("publicNote"), 1000, false);
    r.address = Rules.text(b.get("address"), 500, false);
    if (r.kind.equals("HOME_VISIT")) Rules.check(!r.address.isBlank(), "ADDRESS_REQUIRED");
    r.careNotes = Rules.paragraph(b.get("careNotes"), 1800, false);
    r.petId = p.id;
    r.ownerId = p.ownerId;
    r.departmentId = p.departmentId;
    r.status = "DRAFT";
    if (id == null) {
      r.createdAt = clock.instant();
      db.save(r);
    } else r.version++;
    access.audit("REQUEST_SAVE", r.id, r.departmentId);
    return r;
  }

  /** 发布和关闭由本人办理；已有报价保留，选定订单不能由草稿接口取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object requestAction(Long id, String action, Map<String, Object> b) {
    begin("portal");
    var r = ownRequest(id);
    version(r, b);
    switch (action) {
      case "publish" -> {
        Rules.check(r.status.equals("DRAFT"), "STATE_INVALID");
        readyMember();
        dates(r.startsOn, r.endsOn, r.kind.equals("BOARDING") ? 30 : 14);
        var p = ownPet(r.petId);
        Rules.check(p.enabled && speciesEnabled(p.species), "PET_DISABLED");
        Rules.check(petFree(r), "PET_BUSY");
        r.status = "OPEN";
      }
      case "cancel" -> {
        Rules.check(Set.of("DRAFT", "OPEN").contains(r.status), "STATE_INVALID");
        r.status = "CANCELLED";
        for (var o : db.query(Offer.class, "from Offer where requestId=?1", id)) {
          if (o.status.equals("PROPOSED")) {
            o.status = "NOT_SELECTED";
            o.version++;
          }
        }
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    r.version++;
    access.audit("REQUEST_" + action.toUpperCase(Locale.ROOT), id, r.departmentId);
    return r;
  }

  /** 主人读取本人报价及按区域/物种/真实档期计算的候选；无AI宣传。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object requestDetail(Long id) {
    access.require("portal");
    var r = ownRequest(id);
    var candidates =
        r.status.equals("OPEN") && !r.startsOn.isBefore(today())
            ? db.all(Provider.class).stream()
                .filter(
                    p -> fits(p, r) && available(p, r.kind, r.visitWindow, r.startsOn, r.endsOn))
                .map(this::providerCard)
                .toList()
            : List.of();
    return map(
        "request",
        r,
        "offers",
        db.query(Offer.class, "from Offer where requestId=?1", id).stream()
            .map(this::offerView)
            .toList(),
        "candidates",
        candidates);
  }

  /** 已审核照护方主动报价，48小时有效；记录需求、资料和宠物版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object propose(Long id, Map<String, Object> b) {
    begin("portal");
    readyMember();
    var p = ownProvider();
    var r = db.get(CareRequest.class, id);
    version(r, b);
    Rules.check(r.status.equals("OPEN") && !r.startsOn.isBefore(today()), "STATE_INVALID");
    Rules.check(
        fits(p, r) && available(p, r.kind, r.visitWindow, r.startsOn, r.endsOn),
        "MATCH_UNAVAILABLE");
    Rules.check(petFree(r), "PET_BUSY");
    var rows = db.query(Offer.class, "from Offer where requestId=?1 and providerId=?2", id, p.id);
    for (var prior : rows) {
      if (prior.status.equals("PROPOSED")) {
        Rules.check(!prior.expiresAt.isAfter(clock.instant()), "OFFER_EXISTS");
        prior.status = "EXPIRED";
        prior.version++;
      }
    }
    var o = new Offer();
    o.requestId = id;
    o.providerId = p.id;
    o.departmentId = r.departmentId;
    o.requestVersion = r.version;
    o.providerVersion = p.version;
    o.petVersion = db.get(Pet.class, r.petId).version;
    o.unitMinor = r.kind.equals("BOARDING") ? p.boardingPriceMinor : p.visitPriceMinor;
    o.units = (int) ChronoUnit.DAYS.between(r.startsOn, r.endsOn);
    o.totalMinor = Math.multiplyExact(o.unitMinor, o.units);
    o.note = Rules.paragraph(b.get("note"), 1000, true);
    o.status = "PROPOSED";
    o.expiresAt = clock.instant().plusSeconds(48 * 3600);
    if (o.id == null) db.save(o);
    access.audit("OFFER_PROPOSE", o.id, o.departmentId);
    return offerView(o);
  }

  /** 提案撤回不释放已选订单，也不改写曾报价金额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object withdrawOffer(Long id, Map<String, Object> b) {
    begin("portal");
    var p = ownProvider();
    var o = db.get(Offer.class, id);
    allowed(o.providerId.equals(p.id));
    version(o, b);
    Rules.check(o.status.equals("PROPOSED"), "STATE_INVALID");
    o.status = "WITHDRAWN";
    o.version++;
    access.audit("OFFER_WITHDRAW", id, o.departmentId);
    return offerView(o);
  }

  /** 主人选定有效报价，二次容量校验及冻结上下文在同一事务完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object selectOffer(Long id, Map<String, Object> b) {
    begin("portal");
    var owner = readyMember();
    var r = ownRequest(id);
    version(r, b);
    Rules.check(r.status.equals("OPEN") && !r.startsOn.isBefore(today()), "STATE_INVALID");
    var o = db.get(Offer.class, Rules.id(b.get("offerId")));
    Rules.check(
        o.requestId.equals(id)
            && o.status.equals("PROPOSED")
            && o.expiresAt.isAfter(clock.instant()),
        "OFFER_INVALID");
    Rules.check(o.version == Rules.id(b.get("offerVersion")), "VERSION_CONFLICT");
    var p = db.get(Provider.class, o.providerId);
    var pet = ownPet(r.petId);
    Rules.check(
        o.requestVersion == r.version
            && o.providerVersion == p.version
            && o.petVersion == pet.version,
        "OFFER_STALE");
    Rules.check(
        fits(p, r) && available(p, r.kind, r.visitWindow, r.startsOn, r.endsOn),
        "CAPACITY_UNAVAILABLE");
    Rules.check(petFree(r), "PET_BUSY");
    limit(CareOrder.class, "max_orders");
    var provider = memberOf(p.accountId);
    Rules.check(!provider.contact.isBlank(), "CONTACT_REQUIRED");
    var order = new CareOrder();
    order.requestId = id;
    order.offerId = o.id;
    order.ownerId = r.ownerId;
    order.providerId = p.id;
    order.providerAccountId = p.accountId;
    order.petId = pet.id;
    order.departmentId = r.departmentId;
    order.reference =
        "PS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    order.kind = r.kind;
    order.visitWindow = r.visitWindow;
    order.startsOn = r.startsOn;
    order.endsOn = r.endsOn;
    order.totalMinor = o.totalMinor;
    order.status = "CONFIRMED";
    order.managedCare = workforce.managed(p.id);
    order.stayStatus = r.kind.equals("BOARDING") ? "EXPECTED" : "NOT_APPLICABLE";
    order.createdAt = clock.instant();
    try {
      order.snapshot =
          json.writeValueAsString(
              map(
                  "petName",
                  pet.name,
                  "species",
                  pet.species,
                  "weightKg",
                  pet.weightKg,
                  "petCareNotes",
                  pet.careNotes,
                  "requestCareNotes",
                  r.careNotes,
                  "ownerName",
                  owner.contactName,
                  "ownerContact",
                  owner.contact,
                  "providerName",
                  p.displayName,
                  "providerContact",
                  provider.contact,
                  "serviceAddress",
                  r.kind.equals("BOARDING") ? p.address : r.address,
                  "city",
                  r.city,
                  "district",
                  r.district,
                  "unitMinor",
                  o.unitMinor,
                  "units",
                  o.units,
                  "totalMinor",
                  o.totalMinor,
                  "currency",
                  "CNY",
                  "offerNote",
                  o.note));
    } catch (Exception e) {
      throw new IllegalStateException("SNAPSHOT_FAILED");
    }
    db.save(order);
    for (var d = r.startsOn; d.isBefore(r.endsOn); d = d.plusDays(1)) {
      for (String task :
          r.kind.equals("BOARDING") ? List.of("FEED", "ACTIVITY", "CLEAN") : List.of("VISIT")) {
        var t = new CareTask();
        t.orderId = order.id;
        t.departmentId = r.departmentId;
        t.taskDate = d;
        t.kind = task;
        t.status = "PENDING";
        db.save(t);
      }
    }
    for (var other : db.query(Offer.class, "from Offer where requestId=?1", id)) {
      if (other.id.equals(o.id)) {
        other.status = "SELECTED";
        other.version++;
      } else if (other.status.equals("PROPOSED")) {
        other.status = "NOT_SELECTED";
        other.version++;
      }
    }
    r.status = "BOOKED";
    r.version++;
    entry(order, null, null, "CONFIRM", note(b));
    access.audit("ORDER_CONFIRM", order.id, order.departmentId);
    events.publishEvent(new CareSignal(order.id, "CONFIRM", order.version, null));
    return orderSummary(order);
  }

  CareEntry entry(CareOrder o, Long task, Long prior, String action, String note) {
    var e = new CareEntry();
    e.orderId = o.id;
    e.departmentId = o.departmentId;
    e.taskId = task;
    e.supersedesId = prior;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = clock.instant();
    return db.save(e);
  }

  /** 现场交接不可覆盖；接回必须填写实际接回人，私有物品说明不发送邮件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void handover(CareOrder o, String kind, Map<String, Object> b, String condition) {
    var h = new CareHandover();
    h.orderId = o.id;
    h.departmentId = o.departmentId;
    h.actorId = access.current().id;
    h.kind = kind;
    h.conditionNote = condition;
    h.belongings = Rules.paragraph(b.get("belongings"), 1800, false);
    h.recipient = Rules.text(b.get("recipient"), 120, kind.equals("CHECK_OUT"));
    h.createdAt = clock.instant();
    db.save(h);
  }

  long netPaid(Long id) {
    return db.query(MoneyEntry.class, "from MoneyEntry where orderId=?1", id).stream()
        .mapToLong(e -> e.kind.equals("RECEIPT") ? e.amountMinor : -e.amountMinor)
        .sum();
  }

  /** 订单详情限双方或授权运营，包括不可变快照、任务、记录和私有照片元数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object orderDetail(Long id) {
    var o = order(id);
    Object snapshot;
    try {
      snapshot = json.readValue(o.snapshot, Map.class);
    } catch (Exception e) {
      throw new IllegalStateException("SNAPSHOT_INVALID");
    }
    return map(
        "order",
        orderSummary(o),
        "today",
        today(),
        "snapshot",
        snapshot,
        "tasks",
        db.query(CareTask.class, "from CareTask where orderId=?1", id),
        "entries",
        db.query(CareEntry.class, "from CareEntry where orderId=?1", id),
        "photos",
        db.query(CarePhoto.class, "from CarePhoto where orderId=?1", id).stream()
            .map(this::photoMeta)
            .toList(),
        "money",
        db.query(MoneyEntry.class, "from MoneyEntry where orderId=?1", id),
        "netPaidMinor",
        netPaid(id),
        "reviews",
        db.query(Review.class, "from Review where orderId=?1", id),
        "assignments",
        db.query(
            CareAssignment.class, "from CareAssignment where orderId=?1 order by taskDate", id),
        "handovers",
        db.query(CareHandover.class, "from CareHandover where orderId=?1", id),
        "canCoordinate",
        access.has("portal") && workforce.coordinate(o),
        "canUpload",
        access.has("portal") && workforce.photoActor(o),
        "taskActionIds",
        access.has("portal")
            ? db.query(CareTask.class, "from CareTask where orderId=?1", id).stream()
                .filter(t -> workforce.taskActor(o, t.taskDate))
                .map(t -> t.id)
                .toList()
            : List.of());
  }

  /** 指定双方状态办理；取消、提前结束、争议和回复均追加历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object orderAction(Long id, String action, Map<String, Object> b) {
    begin("portal");
    var o = order(id);
    version(o, b);
    var actor = access.current().id;
    var text = note(b);
    var tasks = db.query(CareTask.class, "from CareTask where orderId=?1", id);
    switch (action) {
      case "start" -> {
        allowed(workforce.coordinate(o));
        workforce.coverage(o);
        Rules.check(
            o.status.equals("CONFIRMED")
                && !today().isBefore(o.startsOn)
                && today().isBefore(o.endsOn),
            "STATE_INVALID");
        Rules.check(activeAccount(o.ownerId), "PARTICIPANT_DISABLED");
        if (o.kind.equals("BOARDING") && !o.stayStatus.equals("LEGACY")) {
          var p = db.get(Provider.class, o.providerId);
          Rules.check(
              reservations(p.id).stream()
                      .filter(
                          x ->
                              !x.id.equals(id)
                                  && x.kind.equals("BOARDING")
                                  && x.departedAt == null
                                  && !today().isBefore(x.startsOn)
                                  && (today().isBefore(x.endsOn) || x.arrivedAt != null))
                      .count()
                  < p.boardingCapacity,
              "CAPACITY_UNAVAILABLE");
          handover(o, "CHECK_IN", b, text);
          o.arrivedAt = clock.instant();
          o.stayStatus = "ARRIVED";
        }
        o.status = "IN_PROGRESS";
      }
      case "check-out" -> {
        allowed(workforce.coordinate(o));
        Rules.check(
            o.kind.equals("BOARDING")
                && o.status.equals("IN_PROGRESS")
                && o.stayStatus.equals("ARRIVED")
                && o.departedAt == null,
            "STATE_INVALID");
        Rules.check(
            tasks.stream()
                .noneMatch(t -> t.status.equals("PENDING") && !t.taskDate.isAfter(today())),
            "TASKS_PENDING");
        Rules.check(
            today().isBefore(o.endsOn) || tasks.stream().anyMatch(t -> t.taskDate.equals(today())),
            "OVERDUE_TASKS_PENDING");
        handover(o, "CHECK_OUT", b, text);
        o.departedAt = clock.instant();
        o.stayStatus = "DEPARTED";
        for (var t : tasks)
          if (t.status.equals("PENDING")) {
            t.status = "CANCELLED";
            t.version++;
          }
      }
      case "finish" -> {
        allowed(workforce.coordinate(o));
        Rules.check(
            !o.kind.equals("BOARDING")
                || o.stayStatus.equals("LEGACY")
                || o.stayStatus.equals("DEPARTED"),
            "CHECK_OUT_REQUIRED");
        Rules.check(o.status.equals("IN_PROGRESS"), "STATE_INVALID");
        Rules.check(
            tasks.stream()
                .noneMatch(t -> t.status.equals("PENDING") && !t.taskDate.isAfter(today())),
            "TASKS_PENDING");
        for (var t : tasks)
          if (t.status.equals("PENDING")) {
            t.status = "CANCELLED";
            t.version++;
          }
        o.status = "REVIEW";
      }
      case "accept" -> {
        allowed(actor.equals(o.ownerId));
        Rules.check(o.status.equals("REVIEW"), "STATE_INVALID");
        Rules.check(netPaid(id) == o.totalMinor, "BALANCE_PENDING");
        o.status = "COMPLETED";
      }
      case "dispute" -> {
        allowed(actor.equals(o.ownerId));
        Rules.check(o.status.equals("REVIEW"), "STATE_INVALID");
        o.status = "DISPUTED";
      }
      case "respond" -> {
        allowed(workforce.coordinate(o));
        Rules.check(o.status.equals("DISPUTED"), "STATE_INVALID");
        o.status = "REVIEW";
      }
      case "cancel" -> {
        allowed(actor.equals(o.ownerId) || actor.equals(o.providerAccountId));
        Rules.check(o.status.equals("CONFIRMED") && netPaid(id) == 0, "STATE_INVALID");
        o.status = "CANCELLED";
        for (var t : tasks) {
          t.status = "CANCELLED";
          t.version++;
        }
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    o.version++;
    entry(o, null, null, action.toUpperCase(Locale.ROOT), text);
    access.audit("ORDER_" + action.toUpperCase(Locale.ROOT), id, o.departmentId);
    events.publishEvent(
        new CareSignal(id, action.toUpperCase(Locale.ROOT).replace('-', '_'), o.version, null));
    return orderSummary(o);
  }

  /** 真实任务完成不可覆盖；补充说明引用原任务记录，不伪造未来时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object taskAction(Long id, Long taskId, String action, Map<String, Object> b) {
    begin("portal");
    var o = order(id);
    version(o, b);
    Rules.check(o.status.equals("IN_PROGRESS"), "STATE_INVALID");
    var t = db.get(CareTask.class, taskId);
    Rules.check(t.orderId.equals(id), "TASK_INVALID");
    allowed(workforce.taskActor(o, t.taskDate));
    Rules.check(o.departedAt == null, "STATE_INVALID");
    CareEntry e;
    if (action.equals("complete")) {
      Rules.check(t.status.equals("PENDING") && !t.taskDate.isAfter(today()), "TASK_INVALID");
      t.status = "DONE";
      t.version++;
      e = entry(o, t.id, null, "TASK_DONE", note(b));
    } else if (action.equals("correct")) {
      Rules.check(t.status.equals("DONE"), "TASK_INVALID");
      var prior = db.get(CareEntry.class, Rules.id(b.get("entryId")));
      Rules.check(
          prior.orderId.equals(id)
              && Objects.equals(prior.taskId, taskId)
              && Set.of("TASK_DONE", "TASK_CORRECTION").contains(prior.action),
          "ENTRY_INVALID");
      Rules.check(
          db.query(CareEntry.class, "from CareEntry where supersedesId=?1", prior.id).isEmpty(),
          "ENTRY_SUPERSEDED");
      e = entry(o, t.id, prior.id, "TASK_CORRECTION", note(b));
    } else throw new Problem(404, "NOT_FOUND");
    o.version++;
    access.audit("CARE_" + action.toUpperCase(Locale.ROOT), taskId, o.departmentId);
    return e;
  }

  /** 线下收款及退款追加流水，金额不超应收、原收款或已收净额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object money(Long id, Map<String, Object> b) {
    begin("portal");
    var o = order(id);
    allowed(o.providerAccountId.equals(access.current().id));
    version(o, b);
    Rules.check(!o.status.equals("CANCELLED"), "STATE_INVALID");
    var kind = txt(b, "kind", 20);
    var amount = Rules.integer(b.get("amountMinor"), 1, 30000000);
    var e = new MoneyEntry();
    e.orderId = id;
    e.departmentId = o.departmentId;
    e.actorId = access.current().id;
    e.amountMinor = amount;
    e.kind = kind;
    e.reference = txt(b, "reference", 120);
    e.note = Rules.paragraph(b.get("note"), 1000, true);
    e.createdAt = clock.instant();
    if (kind.equals("RECEIPT")) {
      Rules.check(!o.status.equals("COMPLETED"), "STATE_INVALID");
      Rules.check(netPaid(id) + amount <= o.totalMinor, "PAYMENT_EXCEEDS");
    } else if (kind.equals("REFUND")) {
      var original = db.get(MoneyEntry.class, Rules.id(b.get("originalId")));
      Rules.check(
          original.orderId.equals(id) && original.kind.equals("RECEIPT"), "ORIGINAL_INVALID");
      var returned =
          db.query(MoneyEntry.class, "from MoneyEntry where originalId=?1", original.id).stream()
              .mapToLong(x -> x.amountMinor)
              .sum();
      Rules.check(
          returned + amount <= original.amountMinor && amount <= netPaid(id), "REFUND_EXCEEDS");
      e.originalId = original.id;
    } else throw new Problem(400, "INVALID_INPUT");
    Rules.check(
        db.query(
                MoneyEntry.class,
                "from MoneyEntry where orderId=?1 and reference=?2",
                id,
                e.reference)
            .isEmpty(),
        "REFERENCE_EXISTS");
    db.save(e);
    o.version++;
    access.audit("MONEY_" + kind, e.id, o.departmentId);
    return e;
  }

  /** 完成订单主人提交待审核评价；运营审核不是自动篡改内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object review(Long id, Map<String, Object> b) {
    begin("portal");
    var o = order(id);
    allowed(o.ownerId.equals(access.current().id));
    version(o, b);
    Rules.check(o.status.equals("COMPLETED"), "STATE_INVALID");
    Rules.check(
        db.query(Review.class, "from Review where orderId=?1", id).isEmpty(), "REVIEW_EXISTS");
    var r = new Review();
    r.orderId = id;
    r.departmentId = o.departmentId;
    r.providerId = o.providerId;
    r.ownerId = o.ownerId;
    r.rating = Rules.integer(b.get("rating"), 1, 5);
    r.comment = Rules.paragraph(b.get("comment"), 1200, true);
    r.status = "PENDING";
    r.moderationNote = "";
    r.createdAt = clock.instant();
    db.save(r);
    o.version++;
    access.audit("REVIEW_SUBMIT", r.id, r.departmentId);
    return r;
  }

  /** 运营按组织授权审阅服务资料与待审核评价，会员不获得运营资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object moderation() {
    access.require("moderate");
    allowed(!access.role().scope.equals("ASSIGNED"));
    return map(
        "providers",
        db.all(Provider.class).stream().filter(p -> access.department(p.departmentId)).toList(),
        "reviews",
        db.all(Review.class).stream().filter(r -> access.department(r.departmentId)).toList(),
        "orders",
        db.all(CareOrder.class).stream()
            .filter(o -> access.department(o.departmentId))
            .map(this::orderSummary)
            .toList());
  }

  /** 审核或停用服务方，评价批准/驳回均需版本和原因。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object moderate(String kind, Long id, Map<String, Object> b) {
    begin("moderate");
    var status = txt(b, "status", 20);
    var text = note(b);
    if (kind.equals("providers")) {
      var p = db.get(Provider.class, id);
      allowed(operation(p.departmentId));
      version(p, b);
      Rules.check(Set.of("APPROVED", "REJECTED", "SUSPENDED").contains(status), "INVALID_INPUT");
      p.status = status;
      p.reviewNote = text;
      p.version++;
      p.updatedAt = clock.instant();
      access.audit("PROVIDER_" + status, id, p.departmentId);
      return p;
    }
    if (kind.equals("reviews")) {
      var r = db.get(Review.class, id);
      allowed(operation(r.departmentId));
      version(r, b);
      Rules.check(Set.of("APPROVED", "REJECTED").contains(status), "INVALID_INPUT");
      r.status = status;
      r.moderationNote = text;
      r.version++;
      access.audit("REVIEW_" + status, id, r.departmentId);
      return r;
    }
    throw new Problem(404, "NOT_FOUND");
  }

  /** 统计授权运营范围及分状态订单，金额是人工记录而非平台实际收入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object reports() {
    access.require("reports");
    allowed(!access.role().scope.equals("ASSIGNED"));
    var orders =
        db.all(CareOrder.class).stream().filter(o -> access.department(o.departmentId)).toList();
    var ids = orders.stream().map(o -> o.id).collect(java.util.stream.Collectors.toSet());
    var money = db.all(MoneyEntry.class).stream().filter(e -> ids.contains(e.orderId)).toList();
    var states = new TreeMap<String, Long>();
    orders.forEach(o -> states.merge(o.status, 1L, Long::sum));
    return map(
        "orders",
        orders.size(),
        "states",
        states,
        "approvedProviders",
        db.all(Provider.class).stream()
            .filter(p -> access.department(p.departmentId) && published(p))
            .count(),
        "openRequests",
        db.all(CareRequest.class).stream()
            .filter(r -> access.department(r.departmentId) && r.status.equals("OPEN"))
            .count(),
        "recordedReceiptsMinor",
        money.stream().filter(e -> e.kind.equals("RECEIPT")).mapToLong(e -> e.amountMinor).sum(),
        "recordedRefundsMinor",
        money.stream().filter(e -> e.kind.equals("REFUND")).mapToLong(e -> e.amountMinor).sum());
  }

  /** 订单CSV限同样可见范围，不夹带联系人、地址或广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    var o = order(id);
    var s =
        new StringBuilder(
            "reference,kind,starts_on,ends_on,window,status,total_minor,currency\r\n");
    s.append(
            String.join(
                ",",
                List.of(
                        o.reference,
                        o.kind,
                        o.startsOn.toString(),
                        o.endsOn.toString(),
                        o.visitWindow,
                        o.status,
                        Long.toString(o.totalMinor),
                        "CNY")
                    .stream()
                    .map(Rules::csv)
                    .toList()))
        .append("\r\n");
    return s.toString();
  }

  /** 订单按最新身份范围在数据库搜索、计数和分页，不泄露私有快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object orderPage(Map<String, String> filters) {
    var account = access.current();
    var role = access.role();
    boolean operation = role.permissions.contains("moderate") && !role.scope.equals("ASSIGNED");
    allowed(role.permissions.contains("portal") || operation);
    int page = Rules.integer(filters.getOrDefault("page", "1"), 1, 200000);
    int size = Rules.integer(filters.getOrDefault("size", "12"), 1, 50);
    var args = new ArrayList<Object>();
    var conditions = new ArrayList<String>();
    if (!(operation && role.scope.equals("ALL"))) {
      args.add(account.id);
      String scope =
          "(o.ownerId=?1 or o.providerAccountId=?1 or exists (select s.id from ShopStaff s, Provider p, Account pa, Department pd, AccessRole pr where s.providerId=o.providerId and p.id=o.providerId and pa.id=p.accountId and pd.id=pa.departmentId and pr.id=pa.roleId and pa.enabled=true and pd.enabled=true and 'portal' member of pr.permissions and s.accountId=?1 and s.status='ACTIVE' and (s.role='MANAGER' or exists (select a.id from CareAssignment a where a.orderId=o.id and a.accountId=?1)))";
      if (operation) {
        args.add(account.departmentId);
        scope += " or o.departmentId=?2";
      }
      conditions.add(scope + ")");
    }
    String status = Rules.text(filters.get("status"), 20, false);
    if (!status.isEmpty()) {
      Rules.check(
          Set.of("CONFIRMED", "IN_PROGRESS", "REVIEW", "DISPUTED", "COMPLETED", "CANCELLED")
              .contains(status),
          "INVALID_INPUT");
      args.add(status);
      conditions.add("o.status=?" + args.size());
    }
    String query = Rules.text(filters.get("q"), 80, false);
    if (!query.isEmpty()) {
      args.add(
          "%"
              + query
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
      conditions.add("lower(o.reference) like ?" + args.size() + " escape '!'");
    }
    String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    Object[] params = args.toArray();
    long total = db.count("select count(o) from CareOrder o" + where, params);
    int pages = (int) Math.max(1, (total + size - 1) / size);
    int actualPage = Math.min(page, pages);
    var rows =
        db.page(
            CareOrder.class,
            "from CareOrder o" + where + " order by o.id desc",
            (actualPage - 1) * size,
            size,
            params);
    return map(
        "items",
        rows.stream().map(this::orderSummary).toList(),
        "page",
        actualPage,
        "size",
        size,
        "total",
        total,
        "pages",
        pages);
  }
}
