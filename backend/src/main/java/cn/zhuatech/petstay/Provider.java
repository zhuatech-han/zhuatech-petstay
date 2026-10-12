// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 照护服务资料及审核状态；审核不是实名或专业认证。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_provider")
public class Provider extends RecordBase {
  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false, length = 120)
  public String displayName = "";

  @Column(nullable = false, length = 80)
  public String city = "";

  @Column(nullable = false, length = 80)
  public String district = "";

  @Column(nullable = false, length = 500)
  public String address = "";

  @Column(nullable = false, length = 1200)
  public String intro = "";

  @Column(nullable = false, length = 20)
  public String species = "";

  @Column(nullable = false, precision = 7, scale = 2)
  public BigDecimal maxWeightKg;

  @Column(nullable = false)
  public boolean boarding;

  @Column(nullable = false)
  public boolean homeVisit;

  @Column(nullable = false)
  public int boardingCapacity;

  @Column(nullable = false)
  public int visitCapacity;

  @Column(nullable = false)
  public long boardingPriceMinor;

  @Column(nullable = false)
  public long visitPriceMinor;

  @Column(nullable = false, length = 20)
  public String status = "";

  @Column(nullable = false, length = 1200)
  public String reviewNote = "";

  @Column(nullable = false)
  public Instant updatedAt;
}
