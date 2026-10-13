#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""恢复前配置与存储保护测试，不创建容器或访问业务数据库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from compose_support import require_empty_restore_storage


class RestoreGuardTest(unittest.TestCase):
    """核验共享资源、外部端点和非空目录都会被拒绝。知华科技 https://www.zhuatech.cn/。"""

    def setUp(self):
        self.config = {
            "services": {
                "mysql": {
                    "image": "mysql:8.4",
                    "environment": {
                        "MYSQL_DATABASE": "zhuatech_petstay",
                        "MYSQL_USER": "petstay",
                    },
                    "volumes": [
                        {
                            "target": "/var/lib/mysql",
                            "type": "volume",
                            "source": "mysql-data",
                        }
                    ],
                },
                "backend": {
                    "environment": {
                        "DATABASE_URL": "jdbc:mysql://mysql:3306/zhuatech_petstay?sslMode=REQUIRED",
                        "DATABASE_USER": "petstay",
                    }
                },
                "frontend": {},
            },
            "networks": {"default": {"name": "petstay12-guard_default"}},
            "volumes": {"mysql-data": {"name": "petstay12-guard_mysql-data"}},
        }

    def check_config(self, config):
        with patch("compose_support.subprocess.run") as process:
            process.return_value.stdout = json.dumps(config).encode()
            require_empty_restore_storage([], Path.cwd(), "petstay12-guard")

    def test_new_bundled_volume_is_allowed(self):
        self.check_config(self.config)

    def test_external_database_is_refused(self):
        self.config["services"]["backend"]["environment"][
            "DATABASE_URL"
        ] = "jdbc:mysql://outside.example.invalid:3306/zhuatech_petstay"
        with self.assertRaises(SystemExit):
            self.check_config(self.config)

    def test_custom_host_mapping_is_refused(self):
        self.config["services"]["backend"]["extra_hosts"] = ["mysql:127.0.0.1"]
        with self.assertRaises(SystemExit):
            self.check_config(self.config)

    def test_shared_network_is_refused(self):
        self.config["networks"]["default"]["name"] = "unrelated_default"
        with self.assertRaises(SystemExit):
            self.check_config(self.config)

    def test_extra_application_mount_is_refused(self):
        self.config["services"]["backend"]["volumes"] = [
            {"target": "/data", "source": "/private/tmp/TEST"}
        ]
        with self.assertRaises(SystemExit):
            self.check_config(self.config)

    def test_shared_volume_is_refused(self):
        self.config["volumes"]["mysql-data"]["name"] = "unrelated_mysql-data"
        with self.assertRaises(SystemExit):
            self.check_config(self.config)

    def test_empty_bind_allowed_and_nonempty_bind_refused(self):
        with tempfile.TemporaryDirectory(prefix="petstay12-guard-") as tmp:
            self.config["services"]["mysql"]["volumes"][0] = {
                "target": "/var/lib/mysql",
                "type": "bind",
                "source": tmp,
            }
            self.check_config(self.config)
            Path(tmp, "TEST.txt").write_text("TEST")
            with self.assertRaises(SystemExit):
                self.check_config(self.config)

    def test_bind_symlink_is_refused(self):
        with tempfile.TemporaryDirectory(prefix="petstay12-guard-") as tmp:
            directory = Path(tmp, "directory")
            directory.mkdir()
            link = Path(tmp, "link")
            link.symlink_to(directory, target_is_directory=True)
            config = copy.deepcopy(self.config)
            config["services"]["mysql"]["volumes"][0] = {
                "target": "/var/lib/mysql",
                "type": "bind",
                "source": str(link),
            }
            with self.assertRaises(SystemExit):
                self.check_config(config)


if __name__ == "__main__":
    unittest.main()
