// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 员工须接受邀请，照护与店长权限不改变平台岗位。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "shop_staff")
public class ShopStaff extends RecordBase {
  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false, length = 20)
  public String role = "CARER";

  @Column(nullable = false, length = 20)
  public String status = "INVITED";

  @Column(nullable = false)
  public Instant createdAt;
}
