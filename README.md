# PetStay · 知华宠物寄养、上门喂养与报价撮合平台

[中文](README.md) | [English](README.en.md)

**知华科技（上海如静知华信息科技有限公司）** · [官网](https://www.zhuatech.cn/) · **公开源码学习版／非商业源码版**。

当前版本 **1.2.0**。PetStay把宠物档案、寄养与上门需求、照护方档期、报价选择、每日照护记录和双方交接放在同一个平台。Java 21 / Spring Boot、Vue 3、MySQL 8及Docker Compose；手机H5与桌面运营后台共用真实数据库。适合学习宠物服务撮合、服务容量管理、订单快照和私有证据管理，也可作为获得商业授权后的定制开发基础。

## 从需求到交接

1. 宠物主注册，填写私有联系资料、猫犬档案，发布寄养或每天一个时段的上门需求。
2. 照护者提交服务资料，运营审核后发布档期。匹配按城市／地区、类型、物种、体重、日期与真实容量筛选。
3. 照护者提出48小时有效报价；主人选择时再次核验并冻结价格、宠物和双方资料，生成订单与每日任务。
4. 指定照护者开始、完成任务、追加修订及私有照片，提交交接；主人确认或提出争议，保留往返记录。
5. 线下收退有原单关联和金额上限；完成后主人评价，经审核才公开。平台不扣款或代收资金。

## 已实现模块

| 使用端 | 功能 |
|---|---|
| 公开手机网页 | 真实审核照护目录、地区／日期／类型筛选、服务详情、已审核评价、中英文切换 |
| 宠物主 | 注册登录、资料／宠物管理、需求草稿与发布、匹配候选、报价选择、私有订单、交接确认／争议、评价、CSV |
| 照护者 | 服务资料审核、档期／容量、符合条件需求、报价及撤回、每日任务、追加修订、私有图片、线下收退登记 |
| 门店与员工 | 邀请与本人接受、负责人／店长／照护员工、班次、逐日分派、跨店冲突检查、入住／接回、超期占位 |
| 通知 | 站内消息／未读数量、每日待办及超期提醒、本人渠道偏好、邮箱验证、SMTP出站状态与有限重试 |
| 运营 | 服务资料／评价审核、范围内订单、统计与审计 |
| 管理 | 账号、角色、团队、菜单权限、系统设置、字典、实时身份复核；公开会员角色与最后管理员保护 |

会员可以同时做主人和申请照护方。服务资料审核不代表实名、兽医、保险或资质认证。准确地址、私有联系资料和照护说明只向确认订单参与方及授权运营人员开放。

## 1.1 长期记录与操作改进

订单在数据库按本人／运营权限搜索及分页，已完成和已取消订单不占未完成订单额度。照片按上海上传日期每日5张、整单100MiB管理，页面显示剩余额度；单张仍限2MiB。分类当前只支持猫犬，未开放类型不能新增启用。字典编辑检查版本，撤回报价和删除档期使用各自说明。

照护方历史报价可按需求编号和状态筛选，每页12条；报价接口仍有10000条保护，并未改成无限查询。

## 1.2 门店运营与提醒

每份照护服务资料对应一家门店或团队。负责人邀请独立账号，员工本人接受后加入；店长协调本店订单，照护员工只看分派订单，款项仍由原服务负责人登记，主人独立验收。员工模式冻结到新订单，关闭设置不能绕过已有分派要求。

每个照护任务日安排一位负责人，寄养当天三项任务共用人员。先发布可工作班次，再分派订单；跨门店检查员工冲突，上门每时段一单、寄养每天最多五只且不能混排上门。已有完成任务不能改责任人，撤销员工或班次前须移交待办。补充历史班次及未完成分派会保留当前操作时间，不能补造过去的完成时间。

新寄养订单记录现场入住、随行物品、实际接回人及接回状态，未接回持续占位；超期每日继续生成照护任务，不自动加价。1.1历史订单标明旧流程，原快照、照片和金额不改写。

业务状态消息一直保留在站内，每日提醒可配置是否启用及平台运营时区整点（默认上海）。部署方配置SMTP后，会员验证本人邮箱并主动开启邮件；发送失败保留状态并有限重试。邮件只含订单号和事件，SMTP接受不等于进入收件箱。未配置SMTP时不发送邮件；短信、微信消息、账号找回和线上支付尚未实现。详见 [门店运营](docs/shop-operations.md)。

## 真实运行页面

以下截图来自1.2独立TEST运行实例，展示宠物档案、需求、独立员工、日程分派、入住接回与通知。名称、地址、联系说明及评价均标为TEST，不代表营业门店或真实客户。图片验收素材使用品牌图并明确标注“不是宠物照片”。

### 登录入口：会员与照护者共用账号

![登录入口](docs/screenshots/02-login.jpg)

### 服务目录：真实筛选及审核资料

![服务目录](docs/screenshots/01-market.jpg)

### 宠物档案：猫犬、体重及私有照护说明

![宠物档案](docs/screenshots/03-pets.jpg)

### 需求详情：匹配候选与实际报价

![需求撮合](docs/screenshots/04-matching.jpg)

### 订单交接：任务、照片、记录及冻结资料

![照护订单](docs/screenshots/05-order.jpg)

### 运营审核与统计：实际平台记录

![运营审核](docs/screenshots/06-operations.jpg)

![运营统计](docs/screenshots/07-reports.jpg)

### 账号权限与系统设置

![角色权限](docs/screenshots/08-roles.jpg)

![系统设置](docs/screenshots/09-settings.jpg)

### 手机H5：390×844浏览器尺寸

![手机照护入口](docs/screenshots/10-mobile.jpg)

### 订单检索：按本人权限查询数据库分页结果

![订单分页](docs/screenshots/13-order-paging.jpg)

### 门店运营：班次、逐日分派与员工权限

![门店日程与员工](docs/screenshots/14-shop-schedule.jpg)

![手机门店日程](docs/screenshots/15-mobile-shop.jpg)

### 通知：本人渠道偏好、验证邮箱与真实站内消息

![提醒与站内消息](docs/screenshots/16-notifications.jpg)

### 照护员工：只能办理分派任务，私有图片沿用同一授权

![员工照护工作区](docs/screenshots/17-staff-care.jpg)

## 架构与目录

浏览器 → Nginx同源代理 → Spring Boot业务／权限服务 → MySQL；Flyway迁移，JPA验证表结构。照护图片保存在数据库并实际解码、去除EXIF；备份同时保留图片和订单快照。业务写入通过平台团队行锁与version控制，针对单主体、单应用实例及低容量使用。

```text
backend/                 Java 21、Spring Boot 4.0.7、业务与身份接口
  src/main/resources/db/migration/  V1身份、V2业务、V3分页、V4门店与通知
  src/test/              业务、权限、照片及真实HTTP集成测试
frontend/                Vue 3.5.43、Vite 8.1.5、手机与桌面界面
  public/brand/          正式LOGO
scripts/                 初始化环境、测试、备份、全新实例恢复与发布检查
docs/                    业务、接口、部署、安全、操作和微信H5说明
compose.yaml             MySQL 8.4、后端、Nginx前端
.env.example             配置名称，不包含真实凭据
LICENSE / NOTICE         自有非商业许可／第三方说明
```

## 环境与安装启动

Docker Engine、Compose v2、Python 3.10+；建议4核、8GiB内存及10GiB空闲空间。独立源码开发需Java 21、Maven 3.9+、Node **24.19.0**与npm，首次构建需要可访问官方依赖仓库。

在仓库根目录执行：

```sh
python3 scripts/init-env.py
docker compose -p petstay config --quiet
docker compose -p petstay up -d --build --wait --wait-timeout 1200
```

访问 http://127.0.0.1:8136/ ，健康检查 http://127.0.0.1:8136/actuator/health 。初始化脚本拒绝覆盖已有.env；随机密码仅保存在本机.env（0600），不打印、不提交。管理员名默认admin，密码读取ADMIN_PASSWORD，**没有固定公开密码**。已有账号密码不会被环境变量覆盖。构建后端必跑测试，前端必跑格式、lint、测试和构建。

全新数据库在慢盘上初始化可能需要数分钟。启动最多等待20分钟；数据库健康检查保留10分钟初始化宽限，检查成功后立即继续，不固定等待10分钟。超时请查看本项目日志并处理原因，不删除数据卷重试。

### 数据库初始化与升级

全新MySQL卷自动创建数据库、执行 `V1__identity.sql` 、`V2__pet_marketplace.sql` 、`V3__order_paging_and_photo_allowance.sql` 和 `V4__shop_operations_and_notifications.sql`，初始化管理员、角色、权限、菜单、字典及设置。**不自动创建业务假数据**。迁移位于 `backend/src/main/resources/db/migration/`；没有重复执行的业务初始化脚本。升级前备份并试恢复，用新增版本迁移，禁止修改已执行版本；Hibernate仅validate。

### 环境变量

| 名称 | 说明 |
|---|---|
| MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD | 独立数据库强密码，由初始化脚本生成 |
| ADMIN_USERNAME / ADMIN_PASSWORD | 首次管理员名及强密码（12–72 UTF-8字节，大小写与数字） |
| WEB_PORT / BIND_ADDRESS | 默认8136／127.0.0.1；避免与其他项目端口冲突 |
| COOKIE_SECURE | 本地false，HTTPS公网必须true |
| MAIL_ENABLED / MAIL_HOST / MAIL_PORT | 邮件默认关闭；启用后配置SMTP服务器和端口 |
| MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM / MAIL_TLS | 环境注入邮件账号、密码、发信地址；REQUIRED或SSL加密，NONE仅用于隔离本地测试 |
| DATABASE_URL / DATABASE_USER | 可选外部MySQL；需证书验证与可信CA，详见部署说明 |

## 测试与验证

```sh
mvn -B -f backend/pom.xml spotless:check clean verify
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements-quality.txt
.venv/bin/python -m black --check scripts
.venv/bin/python -m py_compile scripts/*.py
.venv/bin/python -m unittest discover -s scripts -p "test_*.py"
.venv/bin/python scripts/release-check.py
git diff --check
```

独立空TEST实例（不得对已有业务库执行seed），创建受限状态文件：

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8136 --mode seed --state /private/tmp/petstay-quality-state.json
```

脚本验证注册越权拒绝、匹配与报价、并发选定、容量、快照、状态、任务修订、私有图片、争议、线下原单退款、评价和权限。状态文件含临时凭据，禁止提交。`capture`／`compare`用于重启及恢复前后全量可见数据和图片哈希核对，详见 [验收记录](docs/verification.md)。1.2后端49项、前端10项、MySQL闭环233项及门店流程230项请求／断言已执行；另验证SMTP拒收后的真实重试和V3到V4原数据保留。重启和恢复结果见当前验收记录；不替代公网、实机或生产负载验收。

## 部署、备份与常见问题

[部署与恢复](docs/deployment.md)包含HTTPS、独立域名、环境配置、受限备份和全新实例恢复。备份包含私有资料，脚本短暂停止指定后端、保留图片二进制，禁止公开上传。密码弱或端口冲突会导致启动失败，应查看本项目日志；镜像下载失败先检查网络，不删除原数据卷。会话30分钟或服务重启会重新登录；版本冲突先刷新核对再提交。

手机布局已提供；公众号账号、HTTPS域名和实际微信设备准备后可接入H5链接。**当前未部署公网、未接入微信登录／支付、未提供原生小程序**。详见 [手机H5与微信接入](docs/wechat-H5.md)。

恢复脚本最多等待15分钟检查数据库就绪，先读取容器健康状态，再验证应用账号连接；单次检查最多15秒。初始化期间不反复启动SQL客户端，超时保留独立恢复资源供排查，不覆盖源实例。

可选 [独立数据目录配置](compose.storage.yaml) 支持将数据库放在已创建的独立主机目录；通过 `PETSTAY_MYSQL_DATA_DIR` 指定绝对路径，同时传入两个Compose文件。备份／恢复脚本用重复 `--compose-file` 指定相同配置；恢复拒绝非空目录、符号链接和共享卷。详见部署说明。

## 安全与已知限制

BCrypt、HttpOnly Session、CSRF、实时账号／角色校验、登录与注册节流、版本锁、私有照片及CSV权限已实现。照片≤2MiB、6000边长、9百万像素，按上海上传日期每单每日5张、整单100MiB；保留旧照片。管理员不能冒充订单双方。详见 [安全说明](docs/security.md)及 [业务边界](docs/business.md)。

一份需求一只猫／犬、一个地区和一种服务；寄养1–30晚，上门1–14天，每天固定上午／下午／晚间一个窗口。地区匹配不提供GPS距离或AI。提前结束不自动重算固定价；历史LEGACY订单仍按预约区间计算；新寄养订单实际在养至接回才释放，未接回保守占未来容量。没有短信、实时聊天、线上支付／托管／分账、保险、专业医疗或身份认证。订单支持数据库搜索分页（每页1–50条）；需求和订单上限只计草稿／待匹配与未完成业务，历史保留，会员人数仍按累计限制。旧列表、后台统计及其他目录仍有10000条保护，尚未完成全模块长期分页。站内消息数据库分页，但员工每店100条关系、500条班次仍有容量界限；发送租约重启可能重复邮件，未提供可靠到达收件箱保证。单实例内存Session、写事务平台行锁及这些列表边界不适合未经评估直接多实例／高并发运行。未验证生产可用性，也无真实用户运营指标。

## 授权、贡献与反馈

仅限个人学习交流；未经上海如静知华信息科技有限公司书面授权不得商用。**源码公开、非商业使用**，不是MIT／Apache免费商用许可。README、页面及 [LICENSE](LICENSE)口径一致，第三方版权保留于 [NOTICE](NOTICE)。不提供医疗、服务质量或商业运营担保。商业使用与部署需另行获得授权。

欢迎通过Issue／PR反馈可复现问题，说明版本、步骤及脱敏日志，修改前阅读 [AGENTS.md](AGENTS.md)。安全漏洞请私下联系，勿在公开Issue贴密钥、客户资料或利用细节。

## 联系知华科技

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 <https://www.zhuatech.cn/>，或添加微信 zhuatech、zhuatech2 咨询。

**商业授权或深度定制开发请联系知华科技。** [官网](https://www.zhuatech.cn/) · 微信 **zhuatech**、**zhuatech2**。

| 微信 zhuatech | 微信 zhuatech2 |
|---|---|
| ![微信 zhuatech](docs/images/wechat-zhuatech.png) | ![微信 zhuatech2](docs/images/wechat-zhuatech2.png) |
