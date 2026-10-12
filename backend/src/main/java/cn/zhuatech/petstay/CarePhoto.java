// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 经解码校验的私有PNG/JPEG照片，与数据库一起备份。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_photo")
public class CarePhoto extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public Long actorId;

  @Column(nullable = false, length = 300)
  public String caption = "";

  @Column(nullable = false, length = 30)
  public String mime = "";

  @Column(nullable = false, length = 64)
  public String sha256 = "";

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Lob
  @Column(nullable = false, columnDefinition = "longblob")
  public byte[] data;

  @Column(nullable = false)
  public Instant createdAt;
}
