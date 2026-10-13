// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 本人邮箱验证与渠道偏好；验证码散列不对外返回。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference extends RecordBase {
  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false, length = 254)
  public String email = "";

  @Column(nullable = false)
  public boolean emailEnabled = false;

  @Column(nullable = false)
  public boolean remindersEnabled = true;

  @Column(nullable = false)
  public int reminderHour = 9;

  @Column(nullable = false, length = 10)
  public String language = "zh";

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(nullable = false, length = 254)
  public String pendingEmail = "";

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(nullable = false, length = 64)
  public String codeHash = "";

  @com.fasterxml.jackson.annotation.JsonIgnore public Instant codeExpiresAt;
  @com.fasterxml.jackson.annotation.JsonIgnore public Instant codeRequestedAt;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(nullable = false)
  public int codeAttempts = 0;
}
