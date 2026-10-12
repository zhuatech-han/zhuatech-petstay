// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 宠物属于独立会员，照护要求为私有业务资料。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pet")
public class Pet extends RecordBase {
  @Column(nullable = false)
  public Long ownerId;

  @Column(nullable = false, length = 120)
  public String name = "";

  @Column(nullable = false, length = 60)
  public String species = "";

  @Column(nullable = false, precision = 7, scale = 2)
  public BigDecimal weightKg;

  @Column(nullable = false, length = 1800)
  public String careNotes = "";

  @Column(nullable = false)
  public boolean enabled = true;
}
