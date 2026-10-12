// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 元转换为整数分，不静默舍入或接受负值；界面始终显示人民币元。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function toMinor(value) {
  const text = String(value).trim();
  if (!/^\d{1,6}(\.\d{1,2})?$/.test(text)) throw new Error("INVALID_AMOUNT");
  const [whole, fraction = ""] = text.split(".");
  const amount = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(amount) || amount > 30000000)
    throw new Error("INVALID_AMOUNT");
  return amount;
}
