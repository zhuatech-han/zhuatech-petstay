#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""只恢复到全新独立内置数据库项目，不覆盖旧资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, json, re, subprocess, time, zipfile
from backup import ROOT, invoke, validate_env
from compose_support import prefix as compose_prefix, require_empty_restore_storage


def main():
    p = argparse.ArgumentParser()
    p.add_argument("backup")
    p.add_argument("--project", required=True)
    p.add_argument("--env-file", required=True)
    p.add_argument(
        "--compose-file",
        action="append",
        help="Explicit Compose files, in order; repeat for overrides",
    )
    a = p.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,50}", a.project):
        p.error("Invalid Compose project")
    env = validate_env(a.env_file)
    if env.get("BIND_ADDRESS", "127.0.0.1") not in ["127.0.0.1", "::1"]:
        raise SystemExit("Restore binds only to localhost until separately validated")
    prefix = compose_prefix(ROOT, a.project, a.env_file, a.compose_file)
    if invoke(prefix + ["ps", "-aq"], stdout=subprocess.PIPE).stdout.strip():
        raise SystemExit("Refuse to overwrite existing Compose project")
    for kind, name in [
        ("volume", a.project + "_mysql-data"),
        ("network", a.project + "_default"),
    ]:
        if (
            subprocess.run(
                ["docker", kind, "inspect", name],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            ).returncode
            == 0
        ):
            raise SystemExit("Refuse to overwrite existing resources")
    require_empty_restore_storage(prefix, ROOT, a.project)
    with zipfile.ZipFile(a.backup) as archive:
        if len(archive.namelist()) != 2 or set(archive.namelist()) != {
            "manifest.json",
            "database.sql",
        }:
            raise SystemExit("Invalid archive members")
        if any(member.file_size > 512 * 1024**2 for member in archive.infolist()):
            raise SystemExit("Archive member exceeds restore limit")
        manifest = json.loads(archive.read("manifest.json"))
        data = archive.read("database.sql")
        if manifest.get("product") != "PetStay" or manifest.get("version") != 1:
            raise SystemExit("Incompatible backup")
        if (
            manifest["database"]["size"] != len(data)
            or manifest["database"]["sha256"] != hashlib.sha256(data).hexdigest()
        ):
            raise SystemExit("Backup hash mismatch")
    # Verify before creation. Only restore trusted local backups; SQL is not sandboxed.
    invoke(prefix + ["build"], stdout=subprocess.DEVNULL)
    invoke(prefix + ["up", "-d", "mysql"], stdout=subprocess.DEVNULL)
    ready = False
    deadline = time.monotonic() + 900
    while time.monotonic() < deadline:
        try:
            health = subprocess.run(
                prefix + ["ps", "--format", "{{.Health}}", "mysql"],
                cwd=ROOT,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=min(15, max(0.1, deadline - time.monotonic())),
            )
            if health.returncode != 0 or health.stdout.strip() != "healthy":
                time.sleep(max(0, min(5, deadline - time.monotonic())))
                continue
            test = subprocess.run(
                prefix
                + [
                    "exec",
                    "-T",
                    "mysql",
                    "sh",
                    "-c",
                    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -N -e "SELECT 1" "$MYSQL_DATABASE"',
                ],
                cwd=ROOT,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=min(15, max(0.1, deadline - time.monotonic())),
            )
        except subprocess.TimeoutExpired:
            time.sleep(max(0, min(5, deadline - time.monotonic())))
            continue
        if test.returncode == 0:
            ready = True
            break
        time.sleep(max(0, min(5, deadline - time.monotonic())))
    if not ready:
        raise SystemExit("Database not ready; retain isolated resources for diagnosis")
    invoke(
        prefix
        + [
            "exec",
            "-T",
            "mysql",
            "sh",
            "-c",
            'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"',
        ],
        input=data,
        stdout=subprocess.DEVNULL,
    )
    invoke(
        prefix + ["up", "-d", "--wait", "--wait-timeout", "180", "backend", "frontend"],
        stdout=subprocess.DEVNULL,
    )
    print(
        "Restored into isolated localhost instance. Verify records and private care photos and frozen orders and permissions."
    )


if __name__ == "__main__":
    main()
