// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import jakarta.persistence.*;

/** 可追踪业务身份与显式版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@MappedSuperclass
public abstract class RecordBase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long departmentId = 1L;

  @Column(nullable = false)
  public long version = 1;
}
