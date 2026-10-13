// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** 单实例定时生成提醒、分批投递出站；SMTP连接在业务事务之外。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
@EnableScheduling
public class NotificationWorker {
  final NotificationService notifications;
  final EmailDelivery mail;
  final boolean enabled;

  /** 测试可以关闭定时调度，真实站内队列仍持久化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public NotificationWorker(
      NotificationService notifications,
      EmailDelivery mail,
      @Value("${petstay.notifications.scheduler-enabled:true}") boolean enabled) {
    this.notifications = notifications;
    this.mail = mail;
    this.enabled = enabled;
  }

  /** 每30秒扫描，每批最多10封；失败存安全分类，由队列退避重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void tick() {
    if (!enabled) return;
    notifications.generateReminders();
    for (int i = 0; i < 10; i++) {
      var job = notifications.claim();
      if (job == null) break;
      if (job.skipped()) continue;
      boolean success = false;
      try {
        var names =
            NotificationService.LABELS.getOrDefault(job.event(), job.event()).split(" / ", 2);
        boolean english = job.language().equals("en");
        String event = names[english && names.length > 1 ? 1 : 0];
        mail.send(
            job.destination(),
            "PetStay " + job.reference(),
            job.reference()
                + "\n"
                + event
                + "\n"
                + (english ? "Sign in to PetStay to view details." : "请登录PetStay查看详情。"));
        success = true;
      } catch (Exception ignored) {
        /* 异常正文可能含SMTP地址，禁止记录。 */
      }
      notifications.finish(job.id(), success);
    }
  }
}
