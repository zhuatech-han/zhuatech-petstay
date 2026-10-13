// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 本人站内消息与邮件出站状态，业务事件按收件人去重。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_notification")
public class CareNotification extends RecordBase {
  @Column(nullable = false)
  public Long accountId;

  public Long orderId;

  @Column(nullable = false, length = 160)
  public String eventKey = "";

  @Column(nullable = false, length = 40)
  public String event = "";

  @Column(nullable = false, length = 50)
  public String reference = "";

  @Column(nullable = false, length = 254)
  public String destination = "";

  @Column(nullable = false, length = 10)
  public String language = "zh";

  @Column(nullable = false, length = 20)
  public String deliveryStatus = "IN_APP";

  @Column(nullable = false)
  public int attempts = 0;

  @Column(nullable = false, length = 40)
  public String failureCode = "";

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant nextAttemptAt;

  public Instant readAt;
  public Instant sentAt;
}
