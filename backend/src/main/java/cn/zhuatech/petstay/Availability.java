// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 照护方显式公布的日期、类型和固定上门窗口；禁止重叠。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_availability")
public class Availability extends RecordBase {
  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false, length = 20)
  public String visitWindow = "";

  @Column(nullable = false)
  public LocalDate startsOn;

  @Column(nullable = false)
  public LocalDate endsOn;
}
