// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 订单按日生成指定照护任务，真实时间完成，未来取消保留历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_task")
public class CareTask extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public LocalDate taskDate;

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false, length = 20)
  public String status = "";
}
