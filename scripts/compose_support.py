#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""显式Compose文件与全新恢复存储核验，不覆盖既有目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import json
import re
import subprocess
from pathlib import Path


def prefix(root, project, env_file, files=None):
    """默认原Compose；覆盖文件由操作者明确指定，不修改原配置。知华科技 https://www.zhuatech.cn/。"""
    args = ["docker", "compose", "--env-file", str(env_file), "-p", project]
    for name in files or [root / "compose.yaml"]:
        path = Path(name).resolve()
        if not path.is_file():
            raise SystemExit("Compose file does not exist")
        args.extend(["-f", str(path)])
    return args


def require_empty_restore_storage(args, root, project):
    """只接受本项目数据库和新空存储；拒绝外部网络、共享卷和非空绑定目录。知华科技 https://www.zhuatech.cn/。"""
    result = subprocess.run(
        args + ["config", "--format", "json"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    )
    config = json.loads(result.stdout)
    services = config.get("services", {})
    if set(services) != {"mysql", "backend", "frontend"}:
        raise SystemExit("Restore requires the bundled three services")
    if services["mysql"].get("image") != "mysql:8.4":
        raise SystemExit("Restore requires the bundled MySQL image")
    networks = config.get("networks", {})
    if (
        set(networks) != {"default"}
        or networks["default"].get("external")
        or networks["default"].get("name") != project + "_default"
    ):
        raise SystemExit("Restore requires a new project-owned default network")
    backend_env = services["backend"].get("environment", {})
    mysql_env = services["mysql"].get("environment", {})
    if (
        not re.fullmatch(
            r"jdbc:mysql://mysql:3306/zhuatech_petstay(?:\?.*)?",
            backend_env.get("DATABASE_URL", ""),
        )
        or backend_env.get("DATABASE_USER") != "petstay"
        or mysql_env.get("MYSQL_DATABASE") != "zhuatech_petstay"
        or mysql_env.get("MYSQL_USER") != "petstay"
    ):
        raise SystemExit("Restore refuses external or non-bundled database endpoints")
    if any(s.get("extra_hosts") for s in services.values()):
        raise SystemExit("Restore refuses custom host mappings")
    if any(services[k].get("volumes") for k in ["backend", "frontend"]):
        raise SystemExit("Restore refuses extra application mounts")
    mounts = services["mysql"].get("volumes", [])
    if len(mounts) != 1 or mounts[0].get("target") != "/var/lib/mysql":
        raise SystemExit("Restore requires one isolated MySQL data mount")
    mount = mounts[0]
    if mount.get("type") == "bind":
        path = Path(mount["source"])
        if path.is_symlink() or not path.is_dir() or any(path.iterdir()):
            raise SystemExit(
                "Restore bind directory must already exist, be empty and not be a symlink"
            )
    elif mount.get("type") == "volume":
        volume = config.get("volumes", {}).get(mount.get("source"), {})
        if (
            volume.get("external")
            or volume.get("name") != project + "_mysql-data"
            or volume.get("driver_opts")
        ):
            raise SystemExit("Restore refuses shared or custom-driver volumes")
    else:
        raise SystemExit("Unsupported restore storage type")
