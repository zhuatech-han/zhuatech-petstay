// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 照护方报价冻结版本、金额和48小时期限，选定时再检查容量。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_offer")
public class Offer extends RecordBase {
  @Column(nullable = false)
  public Long requestId;

  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public long requestVersion;

  @Column(nullable = false)
  public long providerVersion;

  @Column(nullable = false)
  public long petVersion;

  @Column(nullable = false)
  public long unitMinor;

  @Column(nullable = false)
  public int units;

  @Column(nullable = false)
  public long totalMinor;

  @Column(nullable = false, length = 1000)
  public String note = "";

  @Column(nullable = false, length = 20)
  public String status = "";

  @Column(nullable = false)
  public Instant expiresAt;
}
