# PetStay 接口说明

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

所有接口同源 `/api`，JSON；认证使用HttpOnly Session Cookie。先GET `/auth/csrf` 获取token与header名称，所有写入带该header；401重新登录，403不自动重放请求。错误为HTTP状态与 `{code,message}`，业务使用version进行冲突检测。

| 资源 | 操作与含义 |
|---|---|
| `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/password` | 注册、登录、退出、实时身份及密码修改；注册不能选择管理角色 |
| `/public/providers`, `/public/providers/{id}/reviews` | 已审核服务目录、公开审核评价 |
| `/options`, `/profile`, `/pets` | 日期／枚举、本人联系资料及宠物 |
| `/provider`, `/opportunities`, `/my-offers` | 本人照护资料及档期、符合条件需求及本人报价；POST/PUT/DELETE `/availability`维护档期 |
| `/requests`, `/requests/{id}` | 本人需求、匹配候选和报价详情 |
| `/requests/{id}/publish`, `/cancel` | 发布草稿、撤销未成单需求 |
| `/requests/{id}/propose` | 合格照护方提出报价，金额为整数分 |
| `/requests/{id}/select` | 主人提交offerId、version、说明，原子成单 |
| `/offers/{id}/withdraw` | 撤回本人未选定报价 |
| `/orders`, `/orders/{id}` | 本人或授权运营范围订单、冻结交接详情 |
| `/orders/{id}/start`, `/finish`, `/accept`, `/dispute`, `/respond`, `/cancel` | 固定双方角色的状态操作 |
| `/orders/{id}/tasks/{task}/complete`, `/correct` | 指定照护者完成任务、追加修订 |
| `/orders/{id}/money`, `/review` | 指定照护者登记真实线下收退；主人评价 |
| `/orders/{id}/photos` | multipart的file、caption、version；PNG/JPEG限2MiB、每日5张（上海上传日期）、整单100MiB |
| `/orders/{id}/photos/{photo}`, `/export.csv` | 私有照片GET/HEAD、授权CSV导出 |
| `/moderate`, `/reports`, `/audit` | 运营列表、统计及审计 |
| `/moderate/{kind}/{id}` | 授权运营审核，含version和审核说明 |
| `/admin/{kind}` | users、roles、departments、settings、dictionaries的权限维护 |

精确字段与校验参见 `Api.java`、`MarketService.java`、`PhotoService.java`；可执行请求示例在 `scripts/smoke.py`。订单页面使用`GET /api/orders/page?page=1&size=12&q=&status=`，返回items/page/size/total/pages，按身份在数据库搜索计数，size为1–50；搜索只查订单号，按字面处理%与_。`GET /api/orders/{id}/photo-allowance`返回上海上传日期、dailyCount/dailyLimit和totalBytes/totalLimitBytes，权限与订单一致。旧`/orders`及后台统计仍有10000条界限，其他列表未全部改为数据库分页。无支付、微信登录、地图、短信或第三方诊疗接口。
