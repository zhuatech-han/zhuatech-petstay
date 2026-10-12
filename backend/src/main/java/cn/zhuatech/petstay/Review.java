// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 完成订单的宠物主评价，运营审核后仅公开昵称和评价。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "care_review")
public class Review extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public Long ownerId;

  @Column(nullable = false)
  public int rating;

  @Column(nullable = false, length = 1200)
  public String comment = "";

  @Column(nullable = false, length = 20)
  public String status = "";

  @Column(nullable = false, length = 1200)
  public String moderationNote = "";

  @Column(nullable = false)
  public Instant createdAt;
}
