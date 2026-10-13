// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 员工在指定门店可工作的日期与窗口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "staff_roster")
public class StaffRoster extends RecordBase {
  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false)
  public LocalDate startsOn;

  @Column(nullable = false)
  public LocalDate endsOn;

  @Column(nullable = false, length = 20)
  public String visitWindow = "DAY";
}
