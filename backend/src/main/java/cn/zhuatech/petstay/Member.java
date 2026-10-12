// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;
import java.time.*;

/** 会员联系资料只对本人和已确认订单双方披露。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "member_profile")
public class Member extends RecordBase {
  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false, length = 120)
  public String contactName = "";

  @Column(nullable = false, length = 160)
  public String contact = "";

  @Column(nullable = false, length = 80)
  public String city = "";

  @Column(nullable = false, length = 80)
  public String district = "";
}
