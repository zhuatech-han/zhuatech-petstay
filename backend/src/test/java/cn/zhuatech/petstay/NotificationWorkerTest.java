// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 邮件语言、无私有载荷及发送失败反馈契约；真实SMTP另在隔离验收中验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class NotificationWorkerTest {
  @Test
  void selectedEmailLanguageAndSafePayloadReachTransport() {
    var queue = mock(NotificationService.class);
    var mail = mock(EmailDelivery.class);
    when(queue.claim())
        .thenReturn(
            new NotificationService.Delivery(
                1L, "test@example.invalid", "PS-TEST", "OVERDUE", "en", false),
            null);
    new NotificationWorker(queue, mail, true).tick();
    var text = ArgumentCaptor.forClass(String.class);
    verify(mail).send(eq("test@example.invalid"), eq("PetStay PS-TEST"), text.capture());
    assertTrue(text.getValue().contains("Collection overdue"));
    assertFalse(text.getValue().contains("超期"));
    assertFalse(text.getValue().contains("address"));
    verify(queue).finish(1L, true);
  }

  @Test
  void transportFailureIsRecordedWithoutExceptionDetailsOrFalseSuccess() {
    var queue = mock(NotificationService.class);
    var mail = mock(EmailDelivery.class);
    when(queue.claim())
        .thenReturn(
            new NotificationService.Delivery(
                2L, "test@example.invalid", "PS-TEST", "DUE", "zh", false),
            null);
    doThrow(new IllegalStateException("TEST secret detail must not escape"))
        .when(mail)
        .send(anyString(), anyString(), anyString());
    assertDoesNotThrow(() -> new NotificationWorker(queue, mail, true).tick());
    verify(queue).finish(2L, false);
  }
}
