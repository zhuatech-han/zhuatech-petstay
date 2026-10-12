// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.petstay;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 非图像、超限载荷和解码尺寸边界验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class PhotoSafetyTest {
  @Test
  void rejectsSvgAndText() {
    assertThrows(Problem.class, () -> PhotoService.sanitize("<svg onload='attack'/>".getBytes()));
  }

  @Test
  void rejectsLargePayload() {
    assertThrows(Problem.class, () -> PhotoService.sanitize(new byte[2 * 1024 * 1024 + 1]));
  }

  @Test
  void rejectsGiganticDimensionsBeforePixelDecode() throws Exception {
    var b = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(6001, 1, BufferedImage.TYPE_INT_RGB), "png", b);
    assertThrows(Problem.class, () -> PhotoService.sanitize(b.toByteArray()));
  }

  @Test
  void reencodesDecodedPngAndHashesSanitizedBytes() throws Exception {
    var b = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", b);
    var result = PhotoService.sanitize(b.toByteArray());
    assertEquals("image/png", result.get("mime"));
    assertEquals(64, result.get("sha256").toString().length());
  }
}
