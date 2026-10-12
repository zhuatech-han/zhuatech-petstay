// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 一只宠物和一项照护需求；地址和说明只由订单授权开放。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_request")
public class CareRequest extends RecordBase {
  @Column(nullable = false)
  public Long ownerId;

  @Column(nullable = false)
  public Long petId;

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false, length = 80)
  public String city = "";

  @Column(nullable = false, length = 80)
  public String district = "";

  @Column(nullable = false, length = 120)
  public String title = "";

  @Column(nullable = false, length = 1000)
  public String publicNote = "";

  @Column(nullable = false, length = 500)
  public String address = "";

  @Column(nullable = false, length = 1800)
  public String careNotes = "";

  @Column(nullable = false, length = 20)
  public String visitWindow = "";

  @Column(nullable = false)
  public LocalDate startsOn;

  @Column(nullable = false)
  public LocalDate endsOn;

  @Column(nullable = false, length = 20)
  public String status = "";

  @Column(nullable = false)
  public Instant createdAt;
}
