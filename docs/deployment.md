# PetStay 部署与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

## 本地

Docker Engine与Compose v2，建议4核／8GiB内存／10GiB空闲空间；首次构建需访问官方镜像与依赖仓库。Java 21、Maven 3.9、Node 24.19.0用于源码开发。仓库根目录执行：

```sh
python3 scripts/init-env.py
# 编辑 .env 设置独立 WEB_PORT；初始化脚本生成随机密码，不打印凭据。
docker compose -p petstay config --quiet
docker compose -p petstay up -d --build --wait --wait-timeout 240
```

http://127.0.0.1:8136/；健康检查 http://127.0.0.1:8136/actuator/health 。管理员名来自ADMIN_USERNAME，密码仅在本地.env；无固定公开密码。已有数据库不会被环境变量密码覆盖。MySQL没有主机公开端口；V1/V2自动迁移，无演示业务数据。启动失败查看该项目logs；常见原因是端口冲突、弱管理员密码、镜像网络不可用或旧库迁移冲突，不删除旧卷解决错误。

## 配置

MYSQL_ROOT_PASSWORD、DATABASE_PASSWORD、ADMIN_USERNAME、ADMIN_PASSWORD为初始化项；WEB_PORT默认8136，BIND_ADDRESS默认127.0.0.1，COOKIE_SECURE本地false。外部MySQL配置DATABASE_URL/DATABASE_USER须设置验证证书的sslMode=VERIFY_IDENTITY及可信CA；默认内置隔离网络使用加密连接。不要把.env、密钥或备份提交Git。

## 公网H5

在独立服务器部署，HTTPS反向代理到本地端口，将COOKIE_SECURE设为true。默认Nginx重建转发头；部署外部代理时需明确可信代理链及真实IP策略，不信任客户端伪造转发头，保留同源 `/api`、上传限制与安全响应头，禁止公开数据库端口。配置域名、证书、备份保留、监控与限流，再在实际微信设备验收；不把本地验证当作公网运营承诺。当前只有账号名与密码，尚无短信、微信登录或微信支付。

## 备份与全新实例恢复

备份包含私有联系资料、冻结订单、图片二进制与流水，必须保存到受限目录（脚本0600），自行加密、离线保存及限定保留期限；不会上传源码。只支持本仓库内置MySQL：

```sh
python3 scripts/backup.py --project petstay --output /secure/path/petstay-backup.zip
# 在受限路径准备独立恢复.env，修改WEB_PORT，保留原管理员密码用于核验。
python3 scripts/restore.py /secure/path/petstay-backup.zip --project petstay-restore --env-file /secure/path/restore.env
```

备份短暂停止该项目后端防止写入，mysqldump含hex-blob；恢复检查成员、大小、哈希，只允许全新项目／卷和localhost绑定，拒绝覆盖旧资源。只恢复可信本地SQL备份。恢复后重新登录并核对订单、照片哈希、任务／报价、款项及权限。源实例仍保留直到核验完成。仅在明确销毁自己的测试实例时才使用带-v的down命令。

## 升级

先备份并试恢复，审查新版本与迁移，构建时Maven测试不可跳过；新增Flyway迁移，禁止改历史版本，启动后检查健康及完整业务。Session为内存存储，重启会退出登录。当前单应用实例，不配置多个副本。
