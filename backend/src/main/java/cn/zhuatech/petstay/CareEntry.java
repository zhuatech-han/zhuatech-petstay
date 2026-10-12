// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 不可变照护、交接和争议说明；修订指向旧记录而不覆盖。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_entry")
public class CareEntry extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = true)
  public Long taskId;

  @Column(nullable = true)
  public Long supersedesId;

  @Column(nullable = false)
  public Long actorId;

  @Column(nullable = false, length = 30)
  public String action = "";

  @Column(nullable = false, length = 1800)
  public String note = "";

  @Column(nullable = false)
  public Instant createdAt;
}
