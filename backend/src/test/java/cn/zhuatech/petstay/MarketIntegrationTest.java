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
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock time;
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
}
