#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""发布前静态图片、品牌、许可和凭据检查；不替代运行验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib
import json
import re
import subprocess
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def main():
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT,
        text=True,
    ).splitlines()
    names = sorted(set(names))
    problems = []
    for name in names:
        path = ROOT / name
        if not path.is_file():
            problems.append("Missing: " + name)
            continue
        if (
            name == ".env"
            or any(
                x in Path(name).parts
                for x in ["node_modules", "target", ".git", "private-backups"]
            )
            or name.endswith("-quality-state.json")
        ):
            problems.append("Private/generated file: " + name)
        if path.suffix in {
            ".java",
            ".js",
            ".vue",
            ".css",
            ".py",
            ".sql",
            ".yml",
            ".yaml",
        } or path.name in {"Dockerfile", ".env.example", "nginx.conf"}:
            text = path.read_text()
            if (
                "上海如静知华信息科技有限公司" not in text[:700]
                or "https://www.zhuatech.cn/" not in text[:700]
            ):
                problems.append("Missing source attribution: " + name)
            if re.search(
                r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}",
                text,
            ):
                problems.append("Potential credential: " + name)
            if name.endswith("Dockerfile") and re.search(
                r"-DskipTests|-Dmaven.test.skip", text
            ):
                problems.append("Skipped tests: " + name)
    for name in ["README.md", "README.en.md"]:
        text = (ROOT / name).read_text()
        for target in re.findall(r'\]\(([^)]+)\)|(?:src|href)="([^"]+)"', text):
            link = target[0] or target[1]
            if link.startswith(("https://", "http://127.0.0.1", "mailto:", "#")):
                continue
            local = link.split("#")[0]
            if not (ROOT / local).is_file():
                problems.append("Broken local link: " + name + " -> " + local)
        if "[中文](README.md) | [English](README.en.md)" not in text:
            problems.append("Missing language links: " + name)
        if "https://www.zhuatech.cn/" not in text:
            problems.append("Missing website: " + name)
    english = (ROOT / "README.en.md").read_text()
    for required in [
        "han@zhuatech.cn",
        "jack@zhuatech.cn",
        "https://wa.me/8617521234993",
    ]:
        if required not in english:
            problems.append("Missing English contact")
    if "wechat-zhuatech" in english:
        problems.append("English README must not include WeChat QR codes")
    cn = (ROOT / "README.md").read_text()
    expected = EXPECTED_QR
    for name, digest in expected.items():
        path = ROOT / "docs/images" / name
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            problems.append("Original QR changed/missing: " + name)
        if "docs/images/" + name not in cn:
            problems.append("Missing Chinese QR: " + name)
    images = []
    for path in (
        list((ROOT / "docs/images").glob("*"))
        + list((ROOT / "docs/screenshots").glob("*"))
        + [ROOT / "frontend/public/brand/logo.jpg"]
    ):
        try:
            with Image.open(path) as picture:
                picture.verify()
            with Image.open(path) as picture:
                images.append(
                    {"path": str(path.relative_to(ROOT)), "size": list(picture.size)}
                )
        except Exception:
            problems.append("Invalid image: " + str(path.relative_to(ROOT)))
    if len(list((ROOT / "docs/screenshots").glob("*"))) < 6:
        problems.append("At least six actual running screenshots are required")
    license_text = (ROOT / "LICENSE").read_text()
    if (
        "上海如静知华信息科技有限公司" not in license_text
        or "不得商用" not in license_text
    ):
        problems.append("Missing non-commercial license/company")
    if problems:
        raise SystemExit("\n".join(problems))
    print(
        json.dumps(
            {
                "result": "passed",
                "publicFiles": len(names),
                "images": images,
                "scope": "Static checks only; actual workflow and visual review are separate",
            },
            ensure_ascii=False,
        )
    )


EXPECTED_QR = {
    "wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
    "wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
}
if __name__ == "__main__":
    main()
