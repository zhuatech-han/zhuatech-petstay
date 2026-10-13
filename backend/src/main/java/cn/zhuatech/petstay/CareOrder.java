// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 选定报价后的不可变业务和联系人快照；双方指定办理。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_order")
public class CareOrder extends RecordBase {
  @Column(nullable = false)
  public Long requestId;

  @Column(nullable = false)
  public Long offerId;

  @Column(nullable = false)
  public Long ownerId;

  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public Long providerAccountId;

  @Column(nullable = false)
  public Long petId;

  @Column(nullable = false, length = 50)
  public String reference = "";

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false, length = 20)
  public String visitWindow = "";

  @Column(nullable = false)
  public LocalDate startsOn;

  @Column(nullable = false)
  public LocalDate endsOn;

  @Column(nullable = false)
  public long totalMinor;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARCHAR)
  @Column(nullable = false, columnDefinition = "longtext")
  public String snapshot = "";

  @Column(nullable = false, length = 20)
  public String status = "";

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public boolean managedCare = false;

  @Column(nullable = false, length = 20)
  public String stayStatus = "LEGACY";

  public Instant arrivedAt;
  public Instant departedAt;
}
