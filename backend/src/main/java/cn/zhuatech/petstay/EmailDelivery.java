// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** SMTP真实发送，凭据仅从部署环境读取，超时与TLS明确配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class EmailDelivery {
  final JavaMailSenderImpl sender = new JavaMailSenderImpl();
  final boolean enabled;
  final String from;
  final String tls;

  /**
   * 缺省关闭邮件；NONE仅用于隔离本地SMTP验收，公网必须REQUIRED或SSL。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public EmailDelivery(
      @Value("${petstay.mail.enabled:false}") boolean enabled,
      @Value("${petstay.mail.host:}") String host,
      @Value("${petstay.mail.port:587}") int port,
      @Value("${petstay.mail.username:}") String username,
      @Value("${petstay.mail.password:}") String password,
      @Value("${petstay.mail.from:}") String from,
      @Value("${petstay.mail.tls:REQUIRED}") String tls) {
    this.enabled = enabled;
    this.from = from;
    this.tls = tls;
    if (enabled
        && (host.isBlank()
            || from.isBlank()
            || !Set.of("REQUIRED", "SSL", "NONE").contains(tls)
            || port < 1
            || port > 65535)) throw new IllegalStateException("MAIL_CONFIG_INVALID");
    sender.setHost(host);
    sender.setPort(port);
    sender.setUsername(username);
    sender.setPassword(password);
    sender.setDefaultEncoding("UTF-8");
    var props = sender.getJavaMailProperties();
    props.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
    props.setProperty("mail.smtp.connectiontimeout", "5000");
    props.setProperty("mail.smtp.timeout", "5000");
    props.setProperty("mail.smtp.writetimeout", "5000");
    props.setProperty("mail.smtp.starttls.enable", Boolean.toString(tls.equals("REQUIRED")));
    props.setProperty("mail.smtp.starttls.required", Boolean.toString(tls.equals("REQUIRED")));
    props.setProperty("mail.smtp.ssl.enable", Boolean.toString(tls.equals("SSL")));
    props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
  }

  /** 显示渠道是否配置，不返回地址、账号或密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean available() {
    return enabled;
  }

  /** 仅发送平台生成的纯文本，不发送客户地址、附件或任意用户正文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void send(String recipient, String subject, String body) {
    Rules.check(enabled, "MAIL_UNAVAILABLE");
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
  }
}
