// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 私有照护照片在数据库持久化；真实解码并去除原EXIF等元数据，不信任文件名或客户端MIME。官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@Service
@Transactional
public class PhotoService {
  final MarketService market;
  final Store db;
  final AccessService access;
  final java.time.Clock clock;

  /** 使用同一订单权限、版本与事务锁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PhotoService(MarketService market, Store db, AccessService access, java.time.Clock clock) {
    this.market = market;
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /**
   * PNG/JPEG最大2MiB、6000边长、900万像素；先读尺寸再解码，重编码移除私有元数据。官网 https://www.zhuatech.cn/；微信 zhuatech /
   * zhuatech2。
   */
  public static Map<String, Object> sanitize(byte[] input) {
    if (input.length == 0 || input.length > 2 * 1024 * 1024)
      throw new Problem(413, "FILE_TOO_LARGE");
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new Problem(400, "IMAGE_INVALID");
      var r = readers.next();
      try {
        var format = r.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("jpeg", "png").contains(format)) throw new Problem(400, "IMAGE_INVALID");
        r.setInput(stream, true, true);
        int w = r.getWidth(0), h = r.getHeight(0);
        if (w < 1 || h < 1 || w > 6000 || h > 6000 || (long) w * h > 9000000)
          throw new Problem(400, "IMAGE_DIMENSIONS");
        var image = r.read(0);
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new Problem(400, "IMAGE_INVALID");
        var bytes = out.toByteArray();
        if (bytes.length > 2 * 1024 * 1024) throw new Problem(413, "FILE_TOO_LARGE");
        return Map.of(
            "data",
            bytes,
            "mime",
            format.equals("jpeg") ? "image/jpeg" : "image/png",
            "sha256",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      } finally {
        r.dispose();
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "IMAGE_INVALID");
    }
  }

  /** 指定照护者在进行中订单上传照片；每单最多20张，版本阻止重复提交覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object upload(Long orderId, String version, String caption, MultipartFile file) {
    market.begin("portal");
    var o = market.order(orderId);
    market.allowed(o.providerAccountId.equals(access.current().id));
    market.version(o, Map.of("version", version));
    Rules.check(o.status.equals("IN_PROGRESS"), "STATE_INVALID");
    Rules.check(
        db.query(CarePhoto.class, "from CarePhoto where orderId=?1", orderId).size() < 20,
        "PHOTO_LIMIT");
    if (file.getSize() > 2 * 1024 * 1024) throw new Problem(413, "FILE_TOO_LARGE");
    byte[] data;
    try {
      data = file.getBytes();
    } catch (IOException e) {
      throw new Problem(400, "IMAGE_INVALID");
    }
    var clean = sanitize(data);
    var p = new CarePhoto();
    p.orderId = orderId;
    p.departmentId = o.departmentId;
    p.actorId = access.current().id;
    p.caption = Rules.paragraph(caption, 300, true);
    p.mime = clean.get("mime").toString();
    p.sha256 = clean.get("sha256").toString();
    p.data = (byte[]) clean.get("data");
    p.createdAt = clock.instant();
    db.save(p);
    o.version++;
    access.audit("CARE_PHOTO", p.id, p.departmentId);
    return market.photoMeta(p);
  }

  /** 照片读取必须先校验订单及照片归属，HEAD与GET共享权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public CarePhoto read(Long orderId, Long id) {
    market.order(orderId);
    var p = db.get(CarePhoto.class, id);
    market.allowed(p.orderId.equals(orderId));
    return p;
  }
}
