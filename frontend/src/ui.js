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
    NIGHT: t("过夜寄养", "Overnight"),
    AM: t("上午 09–12", "Morning 09–12"),
    PM: t("下午 14–17", "Afternoon 14–17"),
    EVENING: t("晚间 18–21", "Evening 18–21"),
  })[k] || k;
export const stateName = (k) =>
  ({
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
