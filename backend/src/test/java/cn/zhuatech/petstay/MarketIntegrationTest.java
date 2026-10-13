// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 真实HTTP/Security/JPA/Flyway流程与隐私、容量、状态、金额测试；只有虚构TEST资料。官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(MarketIntegrationTest.TimeConfig.class)
class MarketIntegrationTest {
  static final String PW = "Aa9" + UUID.randomUUID();

  static class MutableClock extends Clock {
    volatile Instant now = Instant.parse("2026-10-12T04:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return Clock.fixed(now, z);
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:petstay;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("petstay.admin-password", () -> PW);
    r.add("petstay.notifications.scheduler-enabled", () -> "false");
  }

  @Autowired MockMvc mvc;
  @Autowired NotificationService notifications;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean EmailDelivery testMail;
  @Autowired MutableClock time;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession root, owner, provider, other;
  long pet, providerId;
  String city, suffix;

  Map<String, Object> m(Object... v) {
    var b = new LinkedHashMap<String, Object>();
    for (int i = 0; i < v.length; i += 2) b.put(v[i].toString(), v[i + 1]);
    return b;
  }

  MockHttpServletRequestBuilder req(String p, String method, Map<String, Object> b) {
    var r =
        switch (method) {
          case "GET" ->
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api" + p);
          case "POST" ->
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api" + p);
          case "PUT" ->
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api" + p);
          case "DELETE" ->
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                  "/api" + p);
          case "HEAD" ->
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api" + p);
          default -> throw new IllegalArgumentException();
        };
    if (!Set.of("GET", "HEAD").contains(method))
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    return r;
  }

  MvcResult result(MockHttpSession s, String p, String method, Map<String, Object> b)
      throws Exception {
    var r = req(p, method, b);
    if (s != null) r.session(s);
    return mvc.perform(r).andReturn();
  }

  JsonNode ok(MockHttpSession s, String p, String method, Map<String, Object> b) throws Exception {
    var r = result(s, p, method, b);
    assertEquals(200, r.getResponse().getStatus(), p + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode get(MockHttpSession s, String p) throws Exception {
    return ok(s, p, "GET", Map.of());
  }

  JsonNode post(MockHttpSession s, String p, Map<String, Object> b) throws Exception {
    return ok(s, p, "POST", b);
  }

  void denied(MockHttpSession s, String p, String method, Map<String, Object> b, int status)
      throws Exception {
    assertEquals(status, result(s, p, method, b).getResponse().getStatus(), p);
  }

  MockHttpSession login(String u) throws Exception {
    var r =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", u, "password", PW))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  MockHttpSession register(String label) throws Exception {
    String u = "test_" + suffix + "_" + label;
    var r =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/auth/register")
                    .with(csrf())
                    .with(
                        q -> {
                          q.setRemoteAddr("TEST-" + u);
                          return q;
                        })
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            m(
                                "username",
                                u,
                                "password",
                                PW,
                                "displayName",
                                "TEST " + label,
                                "roleId",
                                1,
                                "departmentId",
                                999))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    var s = login(u);
    var profile = get(s, "/profile");
    post(
        s,
        "/profile",
        m(
            "version",
            profile.path("version").asLong(),
            "contactName",
            "TEST " + label,
            "contact",
            "TEST_PRIVATE_" + label,
            "city",
            city,
            "district",
            "TEST_DISTRICT"));
    return s;
  }

  long version(MockHttpSession s, String kind, long id) throws Exception {
    if (kind.equals("orders"))
      return get(s, "/orders/" + id).path("order").path("version").asLong();
    if (kind.equals("requests"))
      return get(s, "/requests/" + id).path("request").path("version").asLong();
    for (var r : get(s, "/" + kind))
      if (r.path("id").asLong() == id) return r.path("version").asLong();
    throw new IllegalStateException();
  }

  @BeforeEach
  void setup() throws Exception {
    time.now = Instant.parse("2026-10-12T04:00:00Z");
    org.mockito.Mockito.doReturn(false).when(testMail).available();
    suffix = UUID.randomUUID().toString().substring(0, 8);
    city = "TEST_CITY_" + suffix;
    root = login("admin");
    owner = register("owner");
    provider = register("provider");
    other = register("other");
    pet =
        post(
                owner,
                "/pets",
                m(
                    "name",
                    "TEST猫",
                    "species",
                    "CAT",
                    "weightKg",
                    "4.20",
                    "careNotes",
                    "TEST_PRIVATE_FEED",
                    "enabled",
                    true))
            .path("id")
            .asLong();
    var p = post(provider, "/provider", providerBody());
    providerId = p.path("id").asLong();
    post(
        root,
        "/moderate/providers/" + providerId,
        m("version", 1, "status", "APPROVED", "note", "TEST资料已审阅"));
    availability(provider, "HOME_VISIT", "AM", "2026-10-12", "2026-11-10");
    availability(provider, "HOME_VISIT", "PM", "2026-10-12", "2026-11-10");
    availability(provider, "BOARDING", "NIGHT", "2026-10-12", "2026-11-10");
  }

  Map<String, Object> providerBody() {
    return m(
        "displayName",
        "TEST照护",
        "city",
        city,
        "district",
        "TEST_DISTRICT",
        "address",
        "TEST_PRIVATE_PROVIDER_ADDRESS",
        "intro",
        "TEST猫犬照护",
        "species",
        "BOTH",
        "maxWeightKg",
        "40",
        "boarding",
        true,
        "homeVisit",
        true,
        "boardingCapacity",
        1,
        "visitCapacity",
        1,
        "boardingPriceMinor",
        9000,
        "visitPriceMinor",
        4500);
  }

  JsonNode availability(MockHttpSession s, String kind, String win, String from, String to)
      throws Exception {
    return post(
        s, "/availability", m("kind", kind, "visitWindow", win, "startsOn", from, "endsOn", to));
  }

  long request(MockHttpSession s, long animal, String kind, String win, String from, String to)
      throws Exception {
    var r =
        post(
            s,
            "/requests",
            m(
                "petId",
                animal,
                "kind",
                kind,
                "visitWindow",
                win,
                "startsOn",
                from,
                "endsOn",
                to,
                "city",
                city,
                "district",
                "TEST_DISTRICT",
                "title",
                "TEST照护需求",
                "publicNote",
                "TEST公开说明",
                "address",
                "TEST_PRIVATE_OWNER_ADDRESS",
                "careNotes",
                "TEST_PRIVATE_REQUEST_NOTES"));
    long id = r.path("id").asLong();
    post(s, "/requests/" + id + "/publish", m("version", 1));
    return id;
  }

  long request() throws Exception {
    return request(owner, pet, "HOME_VISIT", "AM", "2026-10-12", "2026-10-13");
  }

  JsonNode quote(long id) throws Exception {
    return post(
        provider,
        "/requests/" + id + "/propose",
        m("version", version(owner, "requests", id), "note", "TEST按资料提供服务"));
  }

  long select(MockHttpSession s, long id, JsonNode offer) throws Exception {
    return post(
            s,
            "/requests/" + id + "/select",
            m(
                "version",
                version(s, "requests", id),
                "offerId",
                offer.path("id").asLong(),
                "offerVersion",
                offer.path("version").asLong(),
                "note",
                "TEST确认服务与固定报价"))
        .path("id")
        .asLong();
  }

  long booked() throws Exception {
    long r = request();
    return select(owner, r, quote(r));
  }

  JsonNode action(MockHttpSession s, long id, String action) throws Exception {
    return post(
        s,
        "/orders/" + id + "/" + action,
        m("version", version(s, "orders", id), "note", "TEST " + action + "实际说明"));
  }

  JsonNode pay(long id, String ref, int amount) throws Exception {
    return post(
        provider,
        "/orders/" + id + "/money",
        m(
            "version",
            version(provider, "orders", id),
            "kind",
            "RECEIPT",
            "amountMinor",
            amount,
            "reference",
            ref,
            "note",
            "TEST已核对线下款项"));
  }

  void tasks(long id) throws Exception {
    for (var t : get(provider, "/orders/" + id).path("tasks")) {
      if (!LocalDate.parse(t.path("taskDate").asText())
          .isAfter(LocalDate.now(time.withZone(ZoneId.of("Asia/Shanghai")))))
        post(
            provider,
            "/orders/" + id + "/tasks/" + t.path("id").asLong() + "/complete",
            m("version", version(provider, "orders", id), "note", "TEST实际照护完成"));
    }
  }

  @Test
  void fullHomeVisitCareReviewAndPrivatePhoto() throws Exception {
    long id = booked();
    var before = get(owner, "/orders/" + id);
    assertEquals(
        "TEST_PRIVATE_OWNER_ADDRESS", before.path("snapshot").path("serviceAddress").asText());
    action(provider, id, "start");
    tasks(id);
    var photo =
        new MockMultipartFile(
            "file",
            "untrusted-name.txt",
            "text/plain",
            Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aLaQAAAAASUVORK5CYII="));
    var upload =
        mvc.perform(
                multipart("/api/orders/" + id + "/photos")
                    .file(photo)
                    .param("version", Long.toString(version(provider, "orders", id)))
                    .param("caption", "TEST照护照片")
                    .session(provider)
                    .with(csrf()))
            .andReturn();
    assertEquals(200, upload.getResponse().getStatus());
    long pid = json.readTree(upload.getResponse().getContentAsString()).path("id").asLong();
    var media = result(owner, "/orders/" + id + "/photos/" + pid, "GET", Map.of());
    assertEquals(200, media.getResponse().getStatus());
    assertEquals("image/png", media.getResponse().getContentType());
    assertTrue(media.getResponse().getContentAsByteArray().length > 0);
    denied(other, "/orders/" + id + "/photos/" + pid, "HEAD", Map.of(), 403);
    pay(id, "TEST_RECEIPT", 4500);
    action(provider, id, "finish");
    action(owner, id, "accept");
    denied(
        provider,
        "/orders/" + id + "/money",
        "POST",
        m(
            "version",
            version(provider, "orders", id),
            "kind",
            "RECEIPT",
            "amountMinor",
            1,
            "reference",
            "TEST_POST_COMPLETION",
            "note",
            "TEST additional receipt denied"),
        409);
    var review =
        post(
            owner,
            "/orders/" + id + "/review",
            m("version", version(owner, "orders", id), "rating", 5, "comment", "TEST实际服务评价"));
    assertEquals(0, get(null, "/public/providers/" + providerId + "/reviews").size());
    post(
        root,
        "/moderate/reviews/" + review.path("id").asLong(),
        m("version", 1, "status", "APPROVED", "note", "TEST评价审核"));
    assertEquals(1, get(null, "/public/providers/" + providerId + "/reviews").size());
    assertFalse(
        get(null, "/public/providers/" + providerId + "/reviews").toString().contains("ownerId"));
  }

  @Test
  void boardingDailyTasksAndEarlyDeparturePreservePrice() throws Exception {
    long r = request(owner, pet, "BOARDING", "NIGHT", "2026-10-12", "2026-10-15"),
        id = select(owner, r, quote(r));
    assertEquals(9, get(provider, "/orders/" + id).path("tasks").size());
    action(provider, id, "start");
    var future = get(provider, "/orders/" + id).path("tasks").get(3);
    denied(
        provider,
        "/orders/" + id + "/tasks/" + future.path("id").asLong() + "/complete",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST future"),
        409);
    tasks(id);
    post(
        provider,
        "/orders/" + id + "/check-out",
        m(
            "version",
            version(provider, "orders", id),
            "note",
            "TEST physical collection",
            "recipient",
            "TEST主人",
            "belongings",
            "TEST物品返还"));
    action(provider, id, "finish");
    var d = get(owner, "/orders/" + id);
    assertEquals(27000, d.path("order").path("totalMinor").asLong());
    assertEquals(
        6,
        java.util.stream.StreamSupport.stream(d.path("tasks").spliterator(), false)
            .filter(t -> t.path("status").asText().equals("CANCELLED"))
            .count());
    pay(id, "TEST_EARLY", 27000);
    action(owner, id, "accept");
  }

  @Test
  void privacyBeforeSelectionAndForeignOrderDenials() throws Exception {
    long r = request();
    var publicRows = get(provider, "/opportunities").toString();
    assertFalse(publicRows.contains("TEST_PRIVATE"));
    assertFalse(publicRows.contains("ownerId"));
    var catalog = get(null, "/public/providers?city=" + city).toString();
    assertFalse(catalog.contains("address"));
    assertFalse(catalog.contains("contact"));
    long id = select(owner, r, quote(r));
    denied(other, "/orders/" + id, "GET", Map.of(), 403);
    denied(other, "/orders/" + id + "/export.csv", "HEAD", Map.of(), 403);
    denied(provider, "/requests/" + r, "GET", Map.of(), 403);
    denied(null, "/orders/" + id, "GET", Map.of(), 401);
  }

  @Test
  void registrationCannotChooseAdminRoleOrTeam() throws Exception {
    var me = get(owner, "/auth/me");
    assertEquals("ASSIGNED", me.path("scope").asText());
    assertEquals(1, me.path("permissions").size());
    assertEquals("portal", me.path("permissions").get(0).asText());
    assertEquals(1, me.path("departmentId").asLong());
    denied(owner, "/admin/users", "GET", Map.of(), 403);
    denied(owner, "/moderate", "GET", Map.of(), 403);
  }

  @Test
  void csrfProtectsRegistrationAndWrites() throws Exception {
    var r =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/pets")
                    .session(owner)
                    .contentType("application/json")
                    .content("{}"))
            .andReturn();
    assertEquals(403, r.getResponse().getStatus());
    r =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/auth/register")
                    .contentType("application/json")
                    .content("{}"))
            .andReturn();
    assertEquals(403, r.getResponse().getStatus());
  }

  @Test
  void lastAdminAndPublicRoleCannotBeWeakened() throws Exception {
    var users = get(root, "/admin/users");
    JsonNode a = null;
    for (var x : users) if (x.path("username").asText().equals("admin")) a = x;
    assertNotNull(a);
    denied(
        root,
        "/admin/users/" + a.path("id").asLong(),
        "PUT",
        m(
            "version",
            a.path("version").asLong(),
            "username",
            "admin",
            "displayName",
            "TEST root",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            false,
            "password",
            ""),
        409);
    denied(
        root,
        "/admin/roles/3",
        "PUT",
        m(
            "version",
            1,
            "name",
            "TEST unsafe",
            "scope",
            "ALL",
            "permissions",
            List.of("portal", "users")),
        409);
    assertEquals("admin", get(root, "/auth/me").path("username").asText());
  }

  @Test
  void currentVersionAndDoubleSelectionPreventDuplicates() throws Exception {
    long r = request();
    var o = quote(r);
    select(owner, r, o);
    denied(
        owner,
        "/requests/" + r + "/select",
        "POST",
        m(
            "version",
            2,
            "offerId",
            o.path("id").asLong(),
            "offerVersion",
            1,
            "note",
            "TEST duplicate"),
        409);
    assertEquals(1, get(owner, "/orders").size());
  }

  @Test
  void capacityRaceSelectsExactlyOneOrder() throws Exception {
    long r1 = request();
    long p2 =
        post(
                other,
                "/pets",
                m(
                    "name",
                    "TEST犬",
                    "species",
                    "DOG",
                    "weightKg",
                    5,
                    "careNotes",
                    "",
                    "enabled",
                    true))
            .path("id")
            .asLong();
    long r2 = request(other, p2, "HOME_VISIT", "AM", "2026-10-12", "2026-10-13");
    var q1 = quote(r1);
    var q2 =
        post(
            provider, "/requests/" + r2 + "/propose", m("version", 2, "note", "TEST second quote"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f1 =
          pool.submit(
              () -> {
                gate.await();
                return result(
                        owner,
                        "/requests/" + r1 + "/select",
                        "POST",
                        m(
                            "version",
                            2,
                            "offerId",
                            q1.path("id").asLong(),
                            "offerVersion",
                            1,
                            "note",
                            "TEST concurrent1"))
                    .getResponse()
                    .getStatus();
              });
      var f2 =
          pool.submit(
              () -> {
                gate.await();
                return result(
                        other,
                        "/requests/" + r2 + "/select",
                        "POST",
                        m(
                            "version",
                            2,
                            "offerId",
                            q2.path("id").asLong(),
                            "offerVersion",
                            1,
                            "note",
                            "TEST concurrent2"))
                    .getResponse()
                    .getStatus();
              });
      gate.countDown();
      var statuses =
          new ArrayList<>(List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    }
    assertEquals(1, get(owner, "/orders").size() + get(other, "/orders").size());
  }

  @Test
  void expiredQuotesCannotBeSelected() throws Exception {
    long r = request(owner, pet, "HOME_VISIT", "AM", "2026-10-16", "2026-10-17");
    var o = quote(r);
    time.now = time.now.plusSeconds(49 * 3600);
    denied(
        owner,
        "/requests/" + r + "/select",
        "POST",
        m(
            "version",
            2,
            "offerId",
            o.path("id").asLong(),
            "offerVersion",
            1,
            "note",
            "TEST expired"),
        409);
  }

  @Test
  void petChangeInvalidatesUnselectedQuote() throws Exception {
    long r = request();
    var o = quote(r);
    ok(
        owner,
        "/pets/" + pet,
        "PUT",
        m(
            "version",
            1,
            "name",
            "TEST revised",
            "species",
            "CAT",
            "weightKg",
            6,
            "careNotes",
            "TEST revised notes",
            "enabled",
            true));
    denied(
        owner,
        "/requests/" + r + "/select",
        "POST",
        m("version", 2, "offerId", o.path("id").asLong(), "offerVersion", 1, "note", "TEST stale"),
        409);
  }

  @Test
  void withdrawnOfferHistoryIsRetainedOnNewQuote() throws Exception {
    long r = request();
    var q = quote(r);
    post(provider, "/offers/" + q.path("id").asLong() + "/withdraw", m("version", 1));
    var newer = quote(r);
    assertNotEquals(q.path("id").asLong(), newer.path("id").asLong());
    var offers = get(owner, "/requests/" + r).path("offers");
    assertEquals(2, offers.size());
    assertEquals("WITHDRAWN", offers.get(0).path("status").asText());
    assertEquals(q.path("totalMinor"), offers.get(0).path("totalMinor"));
  }

  @Test
  void confirmedSnapshotsSurvivePetAndContactEdits() throws Exception {
    long id = booked();
    var snapshot = get(owner, "/orders/" + id).path("snapshot");
    var member = get(owner, "/profile");
    post(
        owner,
        "/profile",
        m(
            "version",
            member.path("version").asLong(),
            "contactName",
            "TEST NEW",
            "contact",
            "TEST NEW CONTACT",
            "city",
            city,
            "district",
            "TEST_DISTRICT"));
    ok(
        owner,
        "/pets/" + pet,
        "PUT",
        m(
            "version",
            1,
            "name",
            "TEST renamed",
            "species",
            "CAT",
            "weightKg",
            6,
            "careNotes",
            "TEST_NEW_CARE",
            "enabled",
            true));
    assertEquals(snapshot, get(owner, "/orders/" + id).path("snapshot"));
  }

  @Test
  void activeOrderPreventsCapacityAndAvailabilityRemoval() throws Exception {
    booked();
    var body = providerBody();
    body.put("version", 2);
    denied(provider, "/provider", "POST", body, 409);
    var available = get(provider, "/provider").path("availability").get(0);
    denied(
        provider, "/availability/" + available.path("id").asLong(), "DELETE", m("version", 1), 409);
  }

  @Test
  void taskCorrectionsAppendWithoutOverwriting() throws Exception {
    long id = booked();
    action(provider, id, "start");
    tasks(id);
    var d = get(provider, "/orders/" + id);
    var t = d.path("tasks").get(0);
    JsonNode old = null;
    for (var e : d.path("entries")) if (e.path("action").asText().equals("TASK_DONE")) old = e;
    assertNotNull(old);
    post(
        provider,
        "/orders/" + id + "/tasks/" + t.path("id").asLong() + "/correct",
        m(
            "version",
            version(provider, "orders", id),
            "entryId",
            old.path("id").asLong(),
            "note",
            "TEST appended correction"));
    var entries = get(owner, "/orders/" + id).path("entries");
    assertEquals(old, entries.get(entries.size() - 2));
    assertEquals(old.path("id"), entries.get(entries.size() - 1).path("supersedesId"));
  }

  @Test
  void providerCannotAcceptAndAdminCannotImpersonateActors() throws Exception {
    long id = booked();
    denied(owner, "/orders/" + id + "/start", "POST", m("version", 1, "note", "TEST wrong"), 403);
    denied(root, "/orders/" + id + "/start", "POST", m("version", 1, "note", "TEST admin"), 403);
    action(provider, id, "start");
    tasks(id);
    action(provider, id, "finish");
    denied(
        provider,
        "/orders/" + id + "/accept",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST wrong"),
        403);
    denied(
        owner,
        "/orders/" + id + "/accept",
        "POST",
        m("version", version(owner, "orders", id), "note", "TEST unpaid"),
        409);
  }

  @Test
  void disputeResponsePreservesBothNotesAndRequiresOwner() throws Exception {
    long id = booked();
    action(provider, id, "start");
    tasks(id);
    pay(id, "TEST_DISPUTE", 4500);
    action(provider, id, "finish");
    action(owner, id, "dispute");
    action(provider, id, "respond");
    action(owner, id, "accept");
    var es = get(owner, "/orders/" + id).path("entries");
    assertTrue(es.toString().contains("DISPUTE"));
    assertTrue(es.toString().contains("RESPOND"));
  }

  @Test
  void paymentsRefundsAndCancellationAreConservative() throws Exception {
    long id = booked();
    denied(
        provider,
        "/orders/" + id + "/money",
        "POST",
        m(
            "version",
            1,
            "kind",
            "RECEIPT",
            "amountMinor",
            4501,
            "reference",
            "TEST_TOO_MUCH",
            "note",
            "TEST amount"),
        409);
    var receipt = pay(id, "TEST_PAID", 4500);
    denied(
        owner,
        "/orders/" + id + "/cancel",
        "POST",
        m("version", version(owner, "orders", id), "note", "TEST paid cancel"),
        409);
    post(
        provider,
        "/orders/" + id + "/money",
        m(
            "version",
            version(provider, "orders", id),
            "kind",
            "REFUND",
            "amountMinor",
            4500,
            "originalId",
            receipt.path("id").asLong(),
            "reference",
            "TEST_REFUND",
            "note",
            "TEST original refund"));
    denied(
        provider,
        "/orders/" + id + "/money",
        "POST",
        m(
            "version",
            version(provider, "orders", id),
            "kind",
            "REFUND",
            "amountMinor",
            1,
            "originalId",
            receipt.path("id").asLong(),
            "reference",
            "TEST_DUP",
            "note",
            "TEST over refund"),
        409);
    action(owner, id, "cancel");
    assertEquals(2, get(owner, "/orders/" + id).path("money").size());
  }

  @Test
  void pendingAndSuspendedProvidersNeverBecomePublicMatches() throws Exception {
    var p = get(provider, "/provider").path("provider");
    post(
        root,
        "/moderate/providers/" + providerId,
        m("version", p.path("version").asLong(), "status", "SUSPENDED", "note", "TEST suspended"));
    long r = request();
    assertEquals(0, get(owner, "/requests/" + r).path("candidates").size());
    assertEquals(0, get(null, "/public/providers?city=" + city).size());
    denied(
        provider,
        "/requests/" + r + "/propose",
        "POST",
        m("version", 2, "note", "TEST suspended quote"),
        409);
  }

  @Test
  void accountDisableImmediatelyRevokesOrdersAndMedia() throws Exception {
    long id = booked();
    var me = get(provider, "/auth/me");
    JsonNode a = null;
    for (var x : get(root, "/admin/users"))
      if (x.path("id").asLong() == me.path("id").asLong()) a = x;
    assertNotNull(a);
    ok(
        root,
        "/admin/users/" + a.path("id").asLong(),
        "PUT",
        m(
            "version",
            a.path("version").asLong(),
            "username",
            a.path("username").asText(),
            "displayName",
            a.path("displayName").asText(),
            "roleId",
            3,
            "departmentId",
            1,
            "enabled",
            false,
            "password",
            ""));
    denied(provider, "/orders/" + id, "GET", Map.of(), 401);
    assertEquals(0, get(null, "/public/providers?city=" + city).size());
  }

  @Test
  void foreignPetAndOccupiedPetAreRejected() throws Exception {
    denied(other, "/requests", "POST", m("petId", pet), 403);
    long r1 = request(), r2 = request();
    var q1 = quote(r1);
    var q2 = quote(r2);
    select(owner, r1, q1);
    denied(
        owner,
        "/requests/" + r2 + "/select",
        "POST",
        m(
            "version",
            2,
            "offerId",
            q2.path("id").asLong(),
            "offerVersion",
            1,
            "note",
            "TEST pet overlap"),
        409);
    denied(owner, "/pets/" + pet, "DELETE", m("version", 1), 409);
  }

  @Test
  void matchingUsesSpeciesWeightAndAvailableWindow() throws Exception {
    long r = request(owner, pet, "HOME_VISIT", "EVENING", "2026-10-12", "2026-10-13");
    assertEquals(0, get(owner, "/requests/" + r).path("candidates").size());
    denied(
        provider,
        "/requests/" + r + "/propose",
        "POST",
        m("version", 2, "note", "TEST no evening"),
        409);
    denied(
        provider,
        "/availability",
        "POST",
        m(
            "kind",
            "HOME_VISIT",
            "visitWindow",
            "AM",
            "startsOn",
            "2026-10-13",
            "endsOn",
            "2026-10-15"),
        409);
  }

  @Test
  void dateAndStateGuardRejectsFutureServiceAndPrematureFinish() throws Exception {
    long r = request(owner, pet, "HOME_VISIT", "AM", "2026-10-16", "2026-10-17"),
        id = select(owner, r, quote(r));
    denied(
        provider, "/orders/" + id + "/start", "POST", m("version", 1, "note", "TEST future"), 409);
    time.now = Instant.parse("2026-10-16T04:00:00Z");
    action(provider, id, "start");
    denied(
        provider,
        "/orders/" + id + "/finish",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST unfinished"),
        409);
  }

  /** 只复制本测试合成记录，用于验证大量历史不会影响活动额度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  long cloneHistory(String table, long source, Map<String, Object> changes) {
    assertTrue(Set.of("care_request", "care_offer", "care_order").contains(table));
    var fields =
        new LinkedHashMap<>(jdbc.queryForMap("select * from " + table + " where id=?", source));
    fields.remove("id");
    fields.putAll(changes);
    var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
    var values = fields.values().toArray();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "insert into "
                      + table
                      + " ("
                      + String.join(",", fields.keySet())
                      + ") values ("
                      + String.join(",", Collections.nCopies(values.length, "?"))
                      + ")",
                  java.sql.Statement.RETURN_GENERATED_KEYS);
          for (int n = 0; n < values.length; n++) statement.setObject(n + 1, values[n]);
          return statement;
        },
        keys);
    return keys.getKey().longValue();
  }

  @Test
  void fiveThousandHistoricalOrdersHaveScopedPagingAndAllowNewBusiness() throws Exception {
    long id = booked();
    var original = jdbc.queryForMap("select * from care_order where id=?", id);
    long requestId = ((Number) original.get("request_id")).longValue();
    long offerId = ((Number) original.get("offer_id")).longValue();
    jdbc.update("update care_order set status='COMPLETED' where id=?", id);
    for (int n = 0; n < 5000; n++) {
      long r = cloneHistory("care_request", requestId, Map.of());
      long offer = cloneHistory("care_offer", offerId, Map.of("request_id", r));
      cloneHistory(
          "care_order",
          id,
          Map.of("request_id", r, "offer_id", offer, "reference", "TEST_HIST_" + suffix + "_" + n));
    }
    var first = get(owner, "/orders/page?size=12");
    assertEquals(5001, first.path("total").asLong());
    assertEquals(12, first.path("items").size());
    assertFalse(first.path("items").get(0).has("snapshot"));
    var second = get(owner, "/orders/page?page=2&size=12");
    assertNotEquals(first.path("items").get(0).path("id"), second.path("items").get(0).path("id"));
    assertEquals(0, get(other, "/orders/page").path("total").asLong());
    assertEquals(5001, get(provider, "/orders/page?status=COMPLETED").path("total").asLong());
    assertEquals(
        1, get(owner, "/orders/page?q=TEST_HIST_" + suffix + "_4999").path("total").asLong());
    assertEquals(0, get(owner, "/orders/page?q=%25").path("total").asLong());
    assertEquals(417, get(owner, "/orders/page?page=9999").path("page").asInt());
    denied(owner, "/orders/page?size=51", "GET", Map.of(), 400);
    var availability = get(provider, "/provider").path("availability");
    long previousId = 0;
    for (var slot : availability) {
      assertTrue(slot.path("id").asLong() > previousId);
      previousId = slot.path("id").asLong();
    }
    long next = booked();
    assertEquals("CONFIRMED", get(owner, "/orders/" + next).path("order").path("status").asText());
  }

  @Test
  void dictionaryRejectsUnsupportedTypesAndDetectsStaleEdits() throws Exception {
    denied(
        root,
        "/admin/dictionaries",
        "POST",
        m("code", "BIRD", "name", "TEST鸟", "nameEn", "TEST Bird", "enabled", true),
        409);
    var rows = get(root, "/admin/dictionaries");
    JsonNode cat = null;
    for (var row : rows) if (row.path("code").asText().equals("CAT")) cat = row;
    assertNotNull(cat);
    long id = cat.path("id").asLong(), ver = cat.path("version").asLong();
    var body =
        m(
            "code",
            "CAT",
            "name",
            cat.path("name").asText(),
            "nameEn",
            cat.path("nameEn").asText(),
            "enabled",
            true,
            "version",
            ver);
    var changed = ok(root, "/admin/dictionaries/" + id, "PUT", body);
    assertEquals(ver + 1, changed.path("version").asLong());
    denied(root, "/admin/dictionaries/" + id, "PUT", body, 409);
    for (var row : get(owner, "/options").path("species"))
      assertTrue(Set.of("CAT", "DOG").contains(row.path("code").asText()));
  }

  /** 实际HTTP上传测试图，不伪造文件格式或真实客户照片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  MvcResult uploadTestPhoto(long id) throws Exception {
    byte[] image =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aLaQAAAAASUVORK5CYII=");
    return mvc.perform(
            multipart("/api/orders/" + id + "/photos")
                .file(new MockMultipartFile("file", "TEST.png", "image/png", image))
                .param("version", Long.toString(version(provider, "orders", id)))
                .param("caption", "TEST格式验收图，不是宠物照片")
                .session(provider)
                .with(csrf()))
        .andReturn();
  }

  @Test
  void thirtyNightBoardingCanKeepDailyPhotosWithoutLosingOldBytes() throws Exception {
    var availability = get(provider, "/provider").path("availability");
    for (var row : availability)
      if (row.path("kind").asText().equals("BOARDING")) {
        ok(
            provider,
            "/availability/" + row.path("id").asLong(),
            "PUT",
            m(
                "version",
                row.path("version").asLong(),
                "kind",
                "BOARDING",
                "visitWindow",
                "NIGHT",
                "startsOn",
                "2026-10-12",
                "endsOn",
                "2026-11-11"));
      }
    long request = request(owner, pet, "BOARDING", "NIGHT", "2026-10-12", "2026-11-11");
    long id = select(owner, request, quote(request));
    action(provider, id, "start");
    String firstHash = null;
    long firstId = 0;
    for (int day = 0; day < 30; day++) {
      time.now = Instant.parse("2026-10-12T04:00:00Z").plus(Duration.ofDays(day));
      var upload = uploadTestPhoto(id);
      assertEquals(200, upload.getResponse().getStatus());
      var photo = json.readTree(upload.getResponse().getContentAsString());
      if (day == 0) {
        firstHash = photo.path("sha256").asText();
        firstId = photo.path("id").asLong();
      }
      assertEquals(1, get(owner, "/orders/" + id + "/photo-allowance").path("dailyCount").asInt());
    }
    assertEquals(30, get(owner, "/orders/" + id).path("photos").size());
    var bytes =
        result(owner, "/orders/" + id + "/photos/" + firstId, "GET", Map.of())
            .getResponse()
            .getContentAsByteArray();
    assertEquals(
        firstHash,
        HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    assertEquals(
        30L * bytes.length,
        get(owner, "/orders/" + id + "/photo-allowance").path("totalBytes").asLong());
    denied(other, "/orders/" + id + "/photo-allowance", "GET", Map.of(), 403);
  }

  @Test
  void dailyPhotoLimitResetsAtShanghaiMidnight() throws Exception {
    long id = booked();
    action(provider, id, "start");
    time.now = Instant.parse("2026-10-12T15:59:59Z");
    for (int n = 0; n < 5; n++) assertEquals(200, uploadTestPhoto(id).getResponse().getStatus());
    assertEquals(409, uploadTestPhoto(id).getResponse().getStatus());
    time.now = Instant.parse("2026-10-12T16:00:00Z");
    assertEquals(
        "2026-10-13", get(owner, "/orders/" + id + "/photo-allowance").path("date").asText());
    assertEquals(0, get(owner, "/orders/" + id + "/photo-allowance").path("dailyCount").asInt());
    assertEquals(200, uploadTestPhoto(id).getResponse().getStatus());
  }

  long account(MockHttpSession session) throws Exception {
    return get(session, "/auth/me").path("id").asLong();
  }

  JsonNode staff(MockHttpSession employee, String role) throws Exception {
    String username = get(employee, "/auth/me").path("username").asText();
    return post(
        provider, "/shops/" + providerId + "/invite", m("username", username, "role", role));
  }

  void enableStaff() throws Exception {
    post(provider, "/shops/" + providerId + "/policy", m("workforceEnabled", true));
  }

  void rosterStaff(MockHttpSession employee) throws Exception {
    post(
        provider,
        "/shops/" + providerId + "/rosters",
        m(
            "accountId",
            account(employee),
            "startsOn",
            "2026-10-12",
            "endsOn",
            "2026-11-10",
            "visitWindow",
            "DAY"));
  }

  void assignStaff(long id, MockHttpSession employee, String date) throws Exception {
    post(
        provider,
        "/orders/" + id + "/assign",
        m(
            "version",
            version(provider, "orders", id),
            "accountId",
            account(employee),
            "taskDate",
            date,
            "note",
            "TEST shift assignment"));
  }

  @Test
  void invitationConsentScopeAndSafeRevocation() throws Exception {
    long id = booked();
    var invitation = staff(other, "CARER");
    denied(other, "/shops/" + providerId + "/calendar", "GET", Map.of(), 403);
    denied(other, "/orders/" + id, "GET", Map.of(), 403);
    denied(
        owner,
        "/staff/" + invitation.path("id").asLong() + "/accept",
        "POST",
        m("version", 1),
        403);
    post(other, "/staff/" + invitation.path("id").asLong() + "/accept", m("version", 1));
    assertEquals(
        0,
        get(other, "/shops/" + providerId + "/calendar?from=2026-10-12&to=2026-10-13")
            .path("orders")
            .size());
    denied(other, "/orders/" + id, "GET", Map.of(), 403);
    denied(
        other,
        "/shops/" + providerId + "/invite",
        "POST",
        m("username", get(owner, "/auth/me").path("username").asText(), "role", "CARER"),
        403);
    post(provider, "/staff/" + invitation.path("id").asLong() + "/revoke", m("version", 2));
    denied(other, "/shops/" + providerId + "/calendar", "GET", Map.of(), 403);
  }

  @Test
  void staffAssignmentsEnforceCoverageResponsibilityAndMoneySeparation() throws Exception {
    enableStaff();
    var invitation = staff(other, "CARER");
    post(other, "/staff/" + invitation.path("id").asLong() + "/accept", m("version", 1));
    long id = booked();
    denied(
        provider,
        "/orders/" + id + "/start",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST missing staff"),
        409);
    denied(
        provider,
        "/orders/" + id + "/assign",
        "POST",
        m(
            "version",
            version(provider, "orders", id),
            "accountId",
            account(other),
            "taskDate",
            "2026-10-12",
            "note",
            "TEST missing roster"),
        409);
    rosterStaff(other);
    assignStaff(id, other, "2026-10-12");
    assertEquals(1, get(other, "/orders/page").path("total").asInt());
    denied(
        provider,
        "/staff/" + invitation.path("id").asLong() + "/revoke",
        "POST",
        m("version", 2),
        409);
    var cal = get(provider, "/shops/" + providerId + "/calendar?from=2026-10-12&to=2026-10-13");
    denied(
        provider,
        "/rosters/" + cal.path("rosters").get(0).path("id").asLong(),
        "DELETE",
        m("version", 1),
        409);
    action(provider, id, "start");
    var task = get(other, "/orders/" + id).path("tasks").get(0).path("id").asLong();
    denied(
        provider,
        "/orders/" + id + "/tasks/" + task + "/complete",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST wrong assignee"),
        403);
    denied(
        other,
        "/orders/" + id + "/money",
        "POST",
        m(
            "version",
            version(other, "orders", id),
            "kind",
            "RECEIPT",
            "amountMinor",
            4500,
            "reference",
            "TEST_NO_FINANCE",
            "note",
            "TEST denied"),
        403);
    post(
        other,
        "/orders/" + id + "/tasks/" + task + "/complete",
        m("version", version(other, "orders", id), "note", "TEST actual employee care"));
    denied(
        provider,
        "/orders/" + id + "/assign",
        "POST",
        m(
            "version",
            version(provider, "orders", id),
            "accountId",
            account(provider),
            "taskDate",
            "2026-10-12",
            "note",
            "TEST done history"),
        409);
    pay(id, "TEST_STAFF_PAYMENT", 4500);
    action(provider, id, "finish");
    action(owner, id, "accept");
    post(provider, "/staff/" + invitation.path("id").asLong() + "/revoke", m("version", 2));
    denied(other, "/orders/" + id, "GET", Map.of(), 403);
    assertEquals(0, get(other, "/orders/page").path("total").asInt());
  }

  @Test
  void managerCoordinatesWithoutImpersonatingOwnerOrFinance() throws Exception {
    var invitation = staff(other, "MANAGER");
    post(other, "/staff/" + invitation.path("id").asLong() + "/accept", m("version", 1));
    long id = booked();
    assertTrue(get(other, "/orders/" + id).path("canCoordinate").asBoolean());
    action(other, id, "start");
    tasks(id);
    action(other, id, "finish");
    denied(
        other,
        "/orders/" + id + "/accept",
        "POST",
        m("version", version(other, "orders", id), "note", "TEST owner impersonation"),
        403);
    denied(
        other,
        "/orders/" + id + "/money",
        "POST",
        m(
            "version",
            version(other, "orders", id),
            "kind",
            "RECEIPT",
            "amountMinor",
            4500,
            "reference",
            "TEST_MANAGER_FINANCE",
            "note",
            "TEST denied"),
        403);
    denied(root, "/shops/" + providerId + "/policy", "POST", m("workforceEnabled", true), 403);
  }

  @Test
  void staffModeFrozenAndOwnerCalendarConflictsAreChecked() throws Exception {
    long old = booked();
    enableStaff();
    assertFalse(get(provider, "/orders/" + old).path("order").path("managedCare").asBoolean());
    action(provider, old, "start");
    // Another shop asks this provider owner to care in the same window: legacy bookings count too.
    var invitation = staff(other, "CARER");
    post(other, "/staff/" + invitation.path("id").asLong() + "/accept", m("version", 1));
    rosterStaff(other);
    var secondPet =
        post(
                owner,
                "/pets",
                m(
                    "name",
                    "TEST second",
                    "species",
                    "CAT",
                    "weightKg",
                    "3",
                    "careNotes",
                    "TEST",
                    "enabled",
                    true))
            .path("id")
            .asLong();
    long r = request(owner, secondPet, "HOME_VISIT", "PM", "2026-10-12", "2026-10-13"),
        id = select(owner, r, quote(r));
    post(
        provider,
        "/shops/" + providerId + "/rosters",
        m(
            "accountId",
            account(provider),
            "startsOn",
            "2026-10-12",
            "endsOn",
            "2026-10-13",
            "visitWindow",
            "DAY"));
    assignStaff(id, provider, "2026-10-12"); // PM differs from original AM, so it is valid.
    assertTrue(get(provider, "/orders/" + id).path("order").path("managedCare").asBoolean());
    var pol = get(provider, "/shops/" + providerId + "/calendar").path("policy");
    post(
        provider,
        "/shops/" + providerId + "/policy",
        m("version", pol.path("version").asLong(), "workforceEnabled", false));
    assertTrue(get(provider, "/orders/" + id).path("order").path("managedCare").asBoolean());
  }

  @Test
  void physicalBoardingCollectionControlsCapacityAndKeepsHandover() throws Exception {
    long r = request(owner, pet, "BOARDING", "NIGHT", "2026-10-12", "2026-10-13"),
        id = select(owner, r, quote(r));
    action(provider, id, "start");
    assertEquals("ARRIVED", get(owner, "/orders/" + id).path("order").path("stayStatus").asText());
    denied(
        provider,
        "/orders/" + id + "/finish",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST before collection"),
        409);
    tasks(id);
    time.now = Instant.parse("2026-10-13T04:00:00Z");
    notifications.generateReminders();
    notifications.generateReminders();
    assertEquals(6, get(provider, "/orders/" + id).path("tasks").size());
    assertTrue(
        get(provider, "/shops/" + providerId + "/calendar")
            .path("orders")
            .get(0)
            .path("overdue")
            .asBoolean());
    var catalog =
        get(null, "/public/providers?kind=BOARDING&startsOn=2026-10-13&endsOn=2026-10-14");
    assertFalse(
        java.util.stream.StreamSupport.stream(catalog.spliterator(), false)
            .anyMatch(p -> p.path("id").asLong() == providerId));
    for (var t : get(provider, "/orders/" + id).path("tasks"))
      if (t.path("status").asText().equals("PENDING"))
        post(
            provider,
            "/orders/" + id + "/tasks/" + t.path("id").asLong() + "/complete",
            m("version", version(provider, "orders", id), "note", "TEST overdue care"));
    denied(
        provider,
        "/orders/" + id + "/check-out",
        "POST",
        m("version", version(provider, "orders", id), "note", "TEST no recipient"),
        400);
    post(
        provider,
        "/orders/" + id + "/check-out",
        m(
            "version",
            version(provider, "orders", id),
            "note",
            "TEST healthy collection",
            "recipient",
            "TEST authorized owner",
            "belongings",
            "TEST returned carrier"));
    assertEquals(2, get(owner, "/orders/" + id).path("handovers").size());
    assertEquals("DEPARTED", get(owner, "/orders/" + id).path("order").path("stayStatus").asText());
    action(provider, id, "finish");
    assertTrue(
        java.util.stream.StreamSupport.stream(
                get(null, "/public/providers?kind=BOARDING&startsOn=2026-10-13&endsOn=2026-10-14")
                    .spliterator(),
                false)
            .anyMatch(p -> p.path("id").asLong() == providerId));
  }

  @Test
  void notificationOwnershipDailyDedupeAndChannelFailureAreVisible() throws Exception {
    long id = booked();
    notifications.generateReminders();
    notifications.generateReminders();
    var inbox = get(owner, "/notifications");
    var records = inbox.path("items");
    assertTrue(records.size() >= 2);
    long due =
        java.util.stream.StreamSupport.stream(records.spliterator(), false)
            .filter(n -> n.path("orderId").asLong() == id && n.path("event").asText().equals("DUE"))
            .count();
    assertEquals(1, due);
    var n = records.get(0);
    denied(
        other,
        "/notifications/" + n.path("id").asLong() + "/read",
        "POST",
        m("version", n.path("version").asLong()),
        403);
    post(
        owner,
        "/notifications/" + n.path("id").asLong() + "/read",
        m("version", n.path("version").asLong()));
    denied(owner, "/notification-email/request", "POST", m("email", "test@example.invalid"), 409);
    denied(
        owner,
        "/notification-preferences",
        "POST",
        m("emailEnabled", true, "remindersEnabled", true, "reminderHour", 9, "language", "zh"),
        409);
    assertFalse(get(owner, "/notification-preferences").toString().contains("codeHash"));
    post(
        owner,
        "/notification-preferences",
        m("emailEnabled", false, "remindersEnabled", false, "reminderHour", 9, "language", "en"));
    time.now = Instant.parse("2026-10-13T04:00:00Z");
    notifications.generateReminders();
    assertFalse(get(owner, "/notifications").toString().contains(":DUE:2026-10-13"));
  }

  @Test
  void emailVerificationAttemptsAndQueueRetryRespectOptIn() throws Exception {
    org.mockito.Mockito.doReturn(true).when(testMail).available();
    var bodies = new ArrayList<String>();
    org.mockito.Mockito.doAnswer(
            call -> {
              bodies.add(call.getArgument(2));
              return null;
            })
        .when(testMail)
        .send(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
    post(owner, "/notification-email/request", m("email", "test-owner@example.invalid"));
    String code = bodies.getFirst().split("code: ")[1].substring(0, 6);
    String wrong = code.equals("000000") ? "111111" : "000000";
    denied(owner, "/notification-email/verify", "POST", m("code", wrong), 409);
    assertEquals(
        1,
        jdbc.queryForObject(
            "select code_attempts from notification_preference where account_id=?",
            Integer.class,
            account(owner)));
    post(owner, "/notification-email/verify", m("code", code));
    var p = get(owner, "/notification-preferences");
    assertFalse(p.path("emailEnabled").asBoolean());
    post(
        owner,
        "/notification-preferences",
        m(
            "version",
            p.path("version").asLong(),
            "emailEnabled",
            true,
            "remindersEnabled",
            true,
            "reminderHour",
            9,
            "language",
            "en"));
    long id = booked();
    var job = notifications.claim();
    assertNotNull(job);
    assertFalse(job.skipped());
    assertEquals("test-owner@example.invalid", job.destination());
    notifications.finish(job.id(), false);
    assertEquals(
        "RETRY",
        jdbc.queryForObject(
            "select delivery_status from care_notification where id=?", String.class, job.id()));
    time.now = time.now.plusSeconds(121);
    var retry = notifications.claim();
    assertEquals(job.id(), retry.id());
    notifications.finish(retry.id(), true);
    assertEquals(
        "SENT",
        jdbc.queryForObject(
            "select delivery_status from care_notification where id=?", String.class, job.id()));
    notifications.generateReminders();
    p = get(owner, "/notification-preferences");
    post(
        owner,
        "/notification-preferences",
        m(
            "version",
            p.path("version").asLong(),
            "emailEnabled",
            true,
            "remindersEnabled",
            false,
            "reminderHour",
            9,
            "language",
            "en"));
    assertTrue(notifications.claim().skipped());
    action(provider, id, "start");
    p = get(owner, "/notification-preferences");
    post(
        owner,
        "/notification-preferences",
        m(
            "version",
            p.path("version").asLong(),
            "emailEnabled",
            false,
            "remindersEnabled",
            true,
            "reminderHour",
            9,
            "language",
            "en"));
    assertTrue(notifications.claim().skipped());
  }

  @Test
  void crossShopDoubleBookingCountsLegacyOwnerWork() throws Exception {
    long first = booked();
    var p = post(other, "/provider", providerBody());
    long secondShop = p.path("id").asLong();
    post(
        root,
        "/moderate/providers/" + secondShop,
        m("version", 1, "status", "APPROVED", "note", "TEST reviewed"));
    availability(other, "HOME_VISIT", "AM", "2026-10-12", "2026-10-13");
    post(other, "/shops/" + secondShop + "/policy", m("workforceEnabled", true));
    var inv =
        post(
            other,
            "/shops/" + secondShop + "/invite",
            m("username", get(provider, "/auth/me").path("username").asText(), "role", "CARER"));
    post(provider, "/staff/" + inv.path("id").asLong() + "/accept", m("version", 1));
    post(
        other,
        "/shops/" + secondShop + "/rosters",
        m(
            "accountId",
            account(provider),
            "startsOn",
            "2026-10-12",
            "endsOn",
            "2026-10-13",
            "visitWindow",
            "AM"));
    long pet2 =
        post(
                owner,
                "/pets",
                m(
                    "name",
                    "TEST second",
                    "species",
                    "CAT",
                    "weightKg",
                    "3",
                    "careNotes",
                    "TEST",
                    "enabled",
                    true))
            .path("id")
            .asLong();
    long r = request(owner, pet2, "HOME_VISIT", "AM", "2026-10-12", "2026-10-13");
    var quote =
        post(
            other,
            "/requests/" + r + "/propose",
            m("version", version(owner, "requests", r), "note", "TEST second shop"));
    long id = select(owner, r, quote);
    denied(
        other,
        "/orders/" + id + "/assign",
        "POST",
        m(
            "version",
            version(other, "orders", id),
            "accountId",
            account(provider),
            "taskDate",
            "2026-10-12",
            "note",
            "TEST duplicate shift"),
        409);
    assertEquals(0, get(other, "/orders/" + id).path("assignments").size());
    denied(provider, "/orders/" + id, "GET", Map.of(), 403);
    assertEquals(1, get(provider, "/orders/page").path("total").asInt());
    assertEquals(first, get(provider, "/orders/page").path("items").get(0).path("id").asLong());
  }

  @Test
  void concurrentAssignmentHasOneWinnerAndNoLostHistory() throws Exception {
    enableStaff();
    var inv = staff(other, "CARER");
    post(other, "/staff/" + inv.path("id").asLong() + "/accept", m("version", 1));
    rosterStaff(other);
    rosterStaff(provider);
    long id = booked();
    long v = version(provider, "orders", id);
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return result(
                        provider,
                        "/orders/" + id + "/assign",
                        "POST",
                        m(
                            "version",
                            v,
                            "accountId",
                            account(other),
                            "taskDate",
                            "2026-10-12",
                            "note",
                            "TEST race A"))
                    .getResponse()
                    .getStatus();
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return result(
                        provider,
                        "/orders/" + id + "/assign",
                        "POST",
                        m(
                            "version",
                            v,
                            "accountId",
                            account(provider),
                            "taskDate",
                            "2026-10-12",
                            "note",
                            "TEST race B"))
                    .getResponse()
                    .getStatus();
              });
      gate.countDown();
      var statuses =
          new ArrayList<>(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      var d = get(provider, "/orders/" + id);
      assertEquals(1, d.path("assignments").size());
      assertEquals(v + 1, d.path("order").path("version").asLong());
      assertEquals(
          1,
          java.util.stream.StreamSupport.stream(d.path("entries").spliterator(), false)
              .filter(e -> e.path("action").asText().equals("ASSIGN"))
              .count());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void platformTimezoneMatchesCareCalendarAndReminderPreferences() throws Exception {
    try {
      jdbc.update("update department set zone=? where id=1", "Asia/Tokyo");
      time.now = Instant.parse("2026-10-12T15:30:00Z");
      assertEquals("2026-10-13", get(provider, "/options").path("today").asText());
      assertEquals(
          get(provider, "/options").path("today").asText(),
          get(provider, "/shops/" + providerId + "/calendar").path("today").asText());
      assertEquals("Asia/Tokyo", get(provider, "/notification-preferences").path("zone").asText());
    } finally {
      jdbc.update("update department set zone=? where id=1", "Asia/Shanghai");
    }
  }
}
