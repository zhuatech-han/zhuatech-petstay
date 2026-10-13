// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 门店排班设置与显式版本，不更改历史订单模式。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "shop_policy")
public class ShopPolicy extends RecordBase {
  @Column(nullable = false)
  public Long providerId;

  @Column(nullable = false)
  public boolean workforceEnabled = false;
}
