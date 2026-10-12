// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 固定资源接口将公共目录、本人工作区、指定双方订单及运营权限分开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class Api {
  final MarketService market;
  final AdminService admin;
  final PhotoService photos;
  final Store db;
  final AccessService access;

  /** 连接真实持久化服务，不接受动态实体名称。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Api(
      MarketService market,
      AdminService admin,
      PhotoService photos,
      Store db,
      AccessService access) {
    this.market = market;
    this.admin = admin;
    this.photos = photos;
    this.db = db;
    this.access = access;
  }

  /** 匿名可读已审核服务卡片；不会公开地址和联系人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/providers")
  public Object catalog(@RequestParam Map<String, String> filters) {
    return market.catalog(filters);
  }

  /** 仅公开已批准的完成订单评价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/providers/{id}/reviews")
  public Object reviews(@PathVariable Long id) {
    return market.publicReviews(id);
  }

  /** 登录用户只能读取本人业务及明确的运营范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  @GetMapping("/{kind}")
  public Object read(@PathVariable String kind) {
    return switch (kind) {
      case "moderate" -> market.moderation();
      case "reports" -> market.reports();
      case "audit" -> audit();
      default -> market.read(kind);
    };
  }

  /** 新增宠物、需求、档期或更新本人资料，权限由服务重新核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return save(kind, null, b);
  }

  /** 带版本维护本人草稿及档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return save(kind, id, b);
  }

  private Object save(String kind, Long id, Map<String, Object> b) {
    return switch (kind) {
      case "profile" -> market.saveProfile(b);
      case "pets" -> market.savePet(id, b);
      case "provider" -> market.saveProvider(b);
      case "availability" -> market.saveAvailability(id, b);
      case "requests" -> market.saveRequest(id, b);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 本人需求报价或订单冻结详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind}/{id}")
  public Object detail(@PathVariable String kind, @PathVariable Long id) {
    return switch (kind) {
      case "requests" -> market.requestDetail(id);
      case "orders" -> market.orderDetail(id);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 只删除没有业务历史的误建宠物和未占用档期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/{kind}/{id}")
  public Object remove(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return switch (kind) {
      case "pets" -> market.deletePet(id, b);
      case "availability" -> market.deleteAvailability(id, b);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 报价、选定、状态及人工收付由固定动作服务执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{kind}/{id}/{action}")
  public Object action(
      @PathVariable String kind,
      @PathVariable Long id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b) {
    return switch (kind) {
      case "requests" ->
          switch (action) {
            case "propose" -> market.propose(id, b);
            case "select" -> market.selectOffer(id, b);
            default -> market.requestAction(id, action, b);
          };
      case "offers" -> {
        Rules.check(action.equals("withdraw"), "INVALID_INPUT");
        yield market.withdrawOffer(id, b);
      }
      case "orders" ->
          switch (action) {
            case "money" -> market.money(id, b);
            case "review" -> market.review(id, b);
            default -> market.orderAction(id, action, b);
          };
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 指定照护者逐项完成或追加任务修订。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/tasks/{task}/{action}")
  public Object task(
      @PathVariable Long id,
      @PathVariable Long task,
      @PathVariable String action,
      @RequestBody Map<String, Object> b) {
    return market.taskAction(id, task, action, b);
  }

  /** 运营审核服务方或评价；会员不能调用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/moderate/{kind}/{id}")
  public Object moderate(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return market.moderate(kind, id, b);
  }

  /** 上传不信任客户端文件名称、扩展名或MIME。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/photos")
  public Object upload(
      @PathVariable Long id,
      @RequestParam String version,
      @RequestParam String caption,
      @RequestParam MultipartFile file) {
    return photos.upload(id, version, caption, file);
  }

  /** 照片GET/HEAD均先校验订单权限，不将私有照片放入公开静态目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}/photos/{photo}")
  public ResponseEntity<byte[]> photo(@PathVariable Long id, @PathVariable Long photo) {
    var p = photos.read(id, photo);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(p.mime))
        .header("Cache-Control", "private, no-store")
        .header(
            "Content-Disposition",
            "inline; filename=care-" + p.id + (p.mime.equals("image/png") ? ".png" : ".jpg"))
        .body(p.data);
  }

  /** 导出只含业务摘要，沿用同样的订单授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}/export.csv")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "attachment; filename=petstay-order-" + id + ".csv")
        .body(market.export(id));
  }

  /** 运营审计不返回密码、联系人、照片或地址内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    if (access.role().scope.equals("ASSIGNED")) throw new Problem(403, "OUT_OF_SCOPE");
    return db.all(AuditEvent.class).stream()
        .filter(e -> access.department(e.departmentId))
        .toList();
  }

  /** 标准后台目录按账号/角色/设置具体权限校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object directory(@PathVariable String kind) {
    return kind.equals("options") ? admin.options() : admin.read(kind);
  }

  /** 新增运营账号及角色，保留最后完整管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object add(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 目录更新包含版本检查，不接受会员更改组织或权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object change(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }
}
