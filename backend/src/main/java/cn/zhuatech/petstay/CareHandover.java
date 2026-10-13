// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 实际入住与接回的不可变现场交接记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_handover")
public class CareHandover extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public Long actorId;

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false, length = 120)
  public String recipient = "";

  @Column(nullable = false, length = 1800)
  public String conditionNote = "";

  @Column(nullable = false, length = 1800)
  public String belongings = "";

  @Column(nullable = false)
  public Instant createdAt;
}
