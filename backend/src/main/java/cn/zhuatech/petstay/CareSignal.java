// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

/** 同事务业务信号，不携带地址、凭据或私有照护内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public record CareSignal(Long orderId, String event, long version, Long extraAccountId) {}
