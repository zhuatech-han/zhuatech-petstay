// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 安全输入边界与导出保护规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class RulesTest {
  @Test
  void fractionalFrameCountsRejected() {
    assertThrows(Problem.class, () -> Rules.integer(new BigDecimal("1.001"), 0, 200));
  }

  @Test
  void countRangeAndOverflowRejected() {
    assertThrows(Problem.class, () -> Rules.integer("201", 0, 200));
    assertThrows(Problem.class, () -> Rules.integer("99999999999999999999", 0, 200));
  }

  @Test
  void positiveIdsRequired() {
    assertThrows(Problem.class, () -> Rules.id("0"));
    assertThrows(Problem.class, () -> Rules.id("1.1"));
    assertEquals(13L, Rules.id("13"));
  }

  @Test
  void csvFormulaAndQuoteEscaping() {
    assertEquals("\"'\u200b=SUM(A1)\"", Rules.csv("\u200b=SUM(A1)"));
    assertEquals("\"a\"\"b\"", Rules.csv("a\"b"));
  }

  @Test
  void paragraphsPreserveLineBreaksButRejectHiddenControls() {
    assertEquals("first\nsecond", Rules.paragraph(" first\r\nsecond ", 100, true));
    assertThrows(Problem.class, () -> Rules.paragraph("a\u0000b", 100, true));
  }

  @Test
  void bcryptBytesAreValidated() {
    assertThrows(Problem.class, () -> AdminService.validatePassword("short"));
    assertThrows(Problem.class, () -> AdminService.validatePassword("Aa9" + "蜂".repeat(24)));
    assertDoesNotThrow(() -> AdminService.validatePassword("LongPrivate9Password"));
  }
}
