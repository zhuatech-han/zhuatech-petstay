// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { toMinor } from "./currency.js";
test("prices preserve exact cents rather than floating point rounding", () => {
  assert.equal(toMinor("90.29"), 9029);
  assert.equal(toMinor("0.01"), 1);
  assert.equal(toMinor("45.5"), 4550);
});
test("fractional cents and negative values are rejected", () => {
  for (const v of ["1.001", "-2", "NaN", "1e3", ""])
    assert.throws(() => toMinor(v));
});
test("maximum transaction amount is enforced", () => {
  assert.equal(toMinor("300000"), 30000000);
  assert.throws(() => toMinor("300000.01"));
});
