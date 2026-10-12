// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库建立运营身份、受限会员岗位和目录；不伪造服务方或评价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  static final List<String> CODES =
      List.of("portal", "moderate", "reports", "users", "roles", "settings", "audit");
  static final long PUBLIC_ROLE_ID = 3L;
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  /** 独立随机初始化密码只从环境读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${petstay.admin-username}") String username,
      @Value("${petstay.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 幂等初始化；已有数据库不重置账号和目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-zA-Z0-9_.-]{3,60}"), "INVALID_INPUT");
    for (String c : CODES) {
      var p = new Permission();
      p.code = c;
      p.name = c;
      db.save(p);
    }
    var d = new Department();
    d.name = "平台运营 / Platform";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var root = role("管理员 / Administrator", "ALL", CODES);
    role("运营审核 / Operations", "DEPARTMENT", List.of("moderate", "reports", "audit"));
    role("宠物主与照护会员 / Member", "ASSIGNED", List.of("portal"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = root.id;
    a.departmentId = d.id;
    db.save(a);
    var member = new Member();
    member.accountId = a.id;
    member.departmentId = d.id;
    db.save(member);
    String[][] menus = {
      {"market", "寻找照护", "Find care", "portal"},
      {"pets", "我的宠物", "My pets", "portal"},
      {"requests", "我的需求", "My requests", "portal"},
      {"orders", "照护订单", "Care orders", "portal"},
      {"provider", "提供照护", "Provide care", "portal"},
      {"moderate", "运营审核", "Moderation", "moderate"},
      {"reports", "平台统计", "Reports", "reports"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles", "roles"},
      {"settings", "平台设置", "Settings", "settings"},
      {"audit", "审计记录", "Audit", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var v : new String[][] {{"DOG", "犬", "Dog"}, {"CAT", "猫", "Cat"}}) {
      var x = new DictionaryEntry();
      x.type = "SPECIES";
      x.code = v[0];
      x.name = v[1];
      x.nameEn = v[2];
      db.save(x);
    }
    for (String c : List.of("max_members", "max_requests", "max_orders")) {
      var s = new SystemSetting();
      s.code = c;
      s.value = "500";
      db.save(s);
    }
  }

  private AccessRole role(String n, String scope, List<String> codes) {
    var r = new AccessRole();
    r.name = n;
    r.scope = scope;
    r.permissions.addAll(codes);
    return db.save(r);
  }
}
