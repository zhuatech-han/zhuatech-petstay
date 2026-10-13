// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 订单日的照护责任人；变更保留照护流水。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_assignment")
public class CareAssignment extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false)
  public LocalDate taskDate;

  @Column(nullable = false, length = 20)
  public String visitWindow = "";
}
