// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref(localStorage.getItem("petstay-language") || "zh");
/** 同一界面的中英文文本，偏好不含登录或业务数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "en" ? en : zh);
/** 准备独立编辑值，不提前改变已保存记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const cloneRecord = (value) => JSON.parse(JSON.stringify(value));
export const money = (n) =>
  new Intl.NumberFormat(language.value === "en" ? "en-CN" : "zh-CN", {
    style: "currency",
    currency: "CNY",
  }).format((n || 0) / 100);
export const kindName = (k) =>
  k === "BOARDING" ? t("宠物寄养", "Boarding") : t("上门照护", "Home visit");
export const windowName = (k) =>
  ({
    DAY: t("整日", "Full day"),
    NIGHT: t("过夜寄养", "Overnight"),
    AM: t("上午 09–12", "Morning 09–12"),
    PM: t("下午 14–17", "Afternoon 14–17"),
    EVENING: t("晚间 18–21", "Evening 18–21"),
  })[k] || k;
/** 宠物类型按当前语言显示，不向操作页面暴露内部代码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const speciesName = (k) =>
  ({
    CAT: t("猫", "Cat"),
    DOG: t("犬", "Dog"),
    BOTH: t("猫与犬", "Cats & dogs"),
  })[k] || t("未支持类型", "Unsupported species");
export const stateName = (k) =>
  ({
    ACTIVE: t("已加入", "Active"),
    INVITED: t("待接受", "Invited"),
    REVOKED: t("已撤销", "Revoked"),
    LEGACY: t("旧版交接流程", "Legacy handover"),
    EXPECTED: t("待入住", "Awaiting arrival"),
    ARRIVED: t("在养", "Boarded"),
    DEPARTED: t("已接回", "Collected"),
    NOT_APPLICABLE: t("无需入住", "No boarding"),
    CHECK_OUT: t("实际接回", "Physical collection"),
    "CHECK-OUT": t("实际接回", "Physical collection"),
    ASSIGN: t("照护分派", "Care assignment"),
    IN_APP: t("仅站内", "In-app only"),
    QUEUED: t("待发送", "Queued"),
    SENDING: t("发送中", "Sending"),
    SENT: t("SMTP已接受", "Accepted by SMTP"),
    RETRY: t("等待重试", "Awaiting retry"),
    FAILED: t("发送失败", "Delivery failed"),
    CONFIRM: t("双方确认", "Booking confirmed"),
    START: t("开始照护", "Care started"),
    FINISH: t("照护提交", "Care submitted"),
    ACCEPT: t("主人确认完成", "Owner confirmed"),
    DISPUTE: t("主人提出争议", "Owner raised issue"),
    RESPOND: t("照护者回复", "Provider response"),
    CANCEL: t("取消订单", "Booking cancelled"),
    DRAFT: t("草稿", "Draft"),
    OPEN: t("待匹配", "Open"),
    BOOKED: t("已匹配", "Booked"),
    CONFIRMED: t("已确认", "Confirmed"),
    IN_PROGRESS: t("照护中", "In progress"),
    REVIEW: t("待主人确认", "Awaiting owner"),
    DISPUTED: t("待处理争议", "Disputed"),
    COMPLETED: t("已完成", "Completed"),
    CANCELLED: t("已取消", "Cancelled"),
    PENDING: t("待处理", "Pending"),
    APPROVED: t("资料已审核", "Approved"),
    REJECTED: t("未通过", "Rejected"),
    SUSPENDED: t("已暂停", "Suspended"),
    PROPOSED: t("已报价", "Proposed"),
    SELECTED: t("已选定", "Selected"),
    NOT_SELECTED: t("未选定", "Not selected"),
    WITHDRAWN: t("已撤回", "Withdrawn"),
    EXPIRED: t("已到期", "Expired"),
    DONE: t("已完成", "Done"),
  })[k] || k;
export const taskName = (k) =>
  ({
    FEED: t("喂养与饮水", "Feeding & water"),
    ACTIVITY: t("活动照护", "Activity"),
    CLEAN: t("环境清洁", "Cleaning"),
    VISIT: t("上门喂养与照护", "Home care visit"),
  })[k] || k;
export const errorText = (e) =>
  ({
    STAFF_CONFLICT: t(
      "员工在此日期或窗口已有照护，无法重复分派。",
      "Staff already have care in this date or window.",
    ),
    ROSTER_REQUIRED: t(
      "请先为员工发布本店对应日期与窗口的班次。",
      "Publish a matching roster in this shop first.",
    ),
    ROSTER_CONFLICT: t(
      "班次与已有日期窗口重叠。",
      "Roster overlaps an existing window.",
    ),
    STAFF_INACTIVE: t(
      "员工、账号或所属门店权限已失效。",
      "Employee, account or shop access is inactive.",
    ),
    ASSIGNMENT_REQUIRED: t(
      "请先在“门店与排期”安排全部照护任务日。",
      "Assign every care day in Shops & schedule first.",
    ),
    ASSIGNMENT_LOCKED: t(
      "当天已有完成任务，不能改写责任人。",
      "Completed tasks prevent reassignment.",
    ),
    ASSIGNMENTS_PENDING: t(
      "请先移交未完成任务，再撤销关系或删除班次。",
      "Transfer pending assignments before removing access or rosters.",
    ),
    CHECK_OUT_REQUIRED: t(
      "请先登记宠物实际接回。",
      "Record physical collection first.",
    ),
    OVERDUE_TASKS_PENDING: t(
      "超期任务正在补充，请刷新完成今日照护后再登记接回。",
      "Refresh and complete today's overdue care before collection.",
    ),
    INVITATION_EXISTS: t(
      "员工已有有效邀请或门店关系。",
      "An invitation or membership already exists.",
    ),
    MEMBER_UNAVAILABLE: t(
      "未找到可用会员，请核对已注册账号名。",
      "No active member found. Check the registered username.",
    ),
    OWNER_FIXED: t(
      "门店负责人不能通过员工邀请修改。",
      "The shop owner cannot be changed by invitation.",
    ),
    MAIL_UNAVAILABLE: t(
      "邮件服务尚未配置，站内消息可用。",
      "Email is not configured. In-app messages are available.",
    ),
    MAIL_FAILED: t(
      "验证邮件发送失败，请检查邮件服务。",
      "Verification email failed. Check SMTP.",
    ),
    EMAIL_NOT_VERIFIED: t(
      "需先配置邮件服务并验证本人邮箱。",
      "Configure SMTP and verify your email first.",
    ),
    EMAIL_INVALID: t("请输入有效邮箱地址。", "Enter a valid email address."),
    EMAIL_RATE_LIMIT: t(
      "验证邮件发送过于频繁，请稍后再试。",
      "Verification emails are rate limited. Try later.",
    ),
    EMAIL_CODE_INVALID: t(
      "验证码不正确，请核对邮件。",
      "Incorrect code. Check your email.",
    ),
    EMAIL_CODE_EXPIRED: t(
      "验证码过期或错误次数过多，请重新验证。",
      "Code expired or attempts exhausted. Request verification again.",
    ),
    NOTIFICATION_RETRY_DENIED: t(
      "无法重试，请核对渠道、权限或发送次数。",
      "Retry denied. Check channel, access and attempt limits.",
    ),
    CONTACT_REQUIRED: t(
      "请先在“我的资料”完善联系人、联系方式和所在地区。",
      "Complete your contact and location in My profile first.",
    ),
    VERSION_CONFLICT: t(
      "记录已变化，请刷新后核对。",
      "Record changed. Refresh and check again.",
    ),
    CAPACITY_UNAVAILABLE: t(
      "档期已被占用，请重新选择照护方。",
      "Capacity is no longer available. Choose again.",
    ),
    MATCH_UNAVAILABLE: t(
      "服务资料或档期不符合这份需求。",
      "This service or availability does not match.",
    ),
    OFFER_STALE: t(
      "宠物或服务资料已变化，请照护者重新报价。",
      "Pet or provider details changed. Request a new offer.",
    ),
    OFFER_INVALID: t(
      "报价已到期或不可选，请重新匹配。",
      "Offer expired or unavailable. Match again.",
    ),
    OFFER_EXISTS: t(
      "已有有效报价；先撤回再重新提交。",
      "An offer already exists. Withdraw it before submitting again.",
    ),
    PUBLIC_ROLE_FIXED: t(
      "公开会员岗位权限固定，不能提升。",
      "The public member role cannot be elevated.",
    ),
    UNAUTHENTICATED: t(
      "会话已失效，请重新登录。",
      "Session expired. Please sign in.",
    ),
    LOGIN_FAILED: t(
      "账号或密码不正确，或账号已停用。",
      "Invalid credentials or disabled account.",
    ),
    PASSWORD_WEAK: t(
      "密码需12–72个UTF-8字节，含大小写字母和数字。",
      "Use 12–72 UTF-8 bytes with uppercase, lowercase and a number.",
    ),
    RESULT_UNKNOWN: t(
      "提交结果未知。请刷新核对，避免重复提交。",
      "Submission result unknown. Refresh before submitting again.",
    ),
    NETWORK_ERROR: t(
      "网络不可用，请稍后刷新。",
      "Network unavailable. Refresh later.",
    ),
    OUT_OF_SCOPE: t(
      "当前账号没有这项操作权限。",
      "This account cannot perform this action.",
    ),
    FORBIDDEN: t(
      "操作被拒绝，请核对权限或重新登录。",
      "Action denied. Check access or sign in again.",
    ),
    PET_BUSY: t(
      "该宠物已有重叠订单。",
      "This pet already has an overlapping booking.",
    ),
    ACTIVE_ORDERS: t(
      "仍有未完成订单，暂不能修改服务资料或占用档期。",
      "Active orders prevent this change.",
    ),
    TASKS_PENDING: t(
      "请先完成截至今天的全部照护任务。",
      "Complete all care tasks due through today.",
    ),
    BALANCE_PENDING: t(
      "线下收款登记尚未与固定报价结清。",
      "Recorded payments do not yet match the fixed quote.",
    ),
    IMAGE_INVALID: t(
      "请选择有效的PNG或JPEG照片。",
      "Choose a valid PNG or JPEG photo.",
    ),
    SPECIES_UNSUPPORTED: t(
      "当前只支持猫和犬；其他类型尚未开放。",
      "Only cats and dogs are currently supported.",
    ),
    PHOTO_DAILY_LIMIT: t(
      "本单今天已用完5张照片额度，明天可继续上传。",
      "This booking has used today's 5-photo allowance. Upload again tomorrow.",
    ),
    PHOTO_STORAGE_LIMIT: t(
      "本单照片已达到100MiB存储额度，请联系负责人核对。",
      "This booking has reached its 100 MiB photo allowance. Contact the responsible person.",
    ),
    FILE_TOO_LARGE: t("照片不能超过2MiB。", "Photo must be at most 2 MiB."),
    WINDOW_CONFLICT: t(
      "该日期与已发布档期重叠。",
      "Dates overlap a published window.",
    ),
    REGISTRATION_UNAVAILABLE: t(
      "当前无法使用此账号名注册，请更换或联系运营人员。",
      "Registration unavailable. Try another name or contact operations.",
    ),
    STATE_INVALID: t(
      "当前状态或服务日期不允许此操作。",
      "This state or service date does not allow this action.",
    ),
    RESOURCE_LIMIT: t(
      "记录容量已满，请联系运营人员。",
      "Record limit reached. Contact operations.",
    ),
    PAYMENT_EXCEEDS: t(
      "登记金额超过应收余额。",
      "Amount exceeds the outstanding balance.",
    ),
    REFUND_EXCEEDS: t(
      "退款金额超过原收款可退余额。",
      "Refund exceeds the original receipt balance.",
    ),
  })[e.message] || t("操作未完成：", "Action failed: ") + e.message;
