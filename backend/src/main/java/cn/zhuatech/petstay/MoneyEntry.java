// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 已核实线下收款和原款退款，非真实扣款及平台代收。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "money_entry")
public class MoneyEntry extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = true)
  public Long originalId;

  @Column(nullable = false)
  public Long actorId;

  @Column(nullable = false)
  public long amountMinor;

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false, length = 120)
  public String reference = "";

  @Column(nullable = false, length = 1000)
  public String note = "";

  @Column(nullable = false)
  public Instant createdAt;
}
