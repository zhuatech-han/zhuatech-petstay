<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, onMounted } from "vue";
import { api } from "../api.js";
import { t, stateName } from "../ui.js";
const props = defineProps({
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "open-order"]);
const inbox = ref(null),
  preferences = ref(null),
  page = ref(1),
  email = ref(""),
  code = ref(""),
  loading = ref(false);
let seq = 0;
/** 本人消息与偏好同步读取，不在浏览器保存验证码或邮箱。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  const n = ++seq;
  loading.value = true;
  try {
    const [a, b] = await Promise.all([
      api("/notifications?page=" + page.value),
      api("/notification-preferences"),
    ]);
    if (n !== seq) return;
    inbox.value = a;
    page.value = a.page;
    preferences.value = b;
  } catch (e) {
    if (n === seq) emit("error", e);
  } finally {
    if (n === seq) loading.value = false;
  }
}
async function save() {
  const p = preferences.value;
  const r = await props.perform("/notification-preferences", "POST", {
    version: p.version,
    emailEnabled: p.emailEnabled,
    remindersEnabled: p.remindersEnabled,
    reminderHour: p.reminderHour,
    language: p.language,
  });
  if (r) await load();
}
async function verify(action) {
  const r = await props.perform(
    "/notification-email/" + action,
    "POST",
    action === "request" ? { email: email.value } : { code: code.value },
  );
  if (r) {
    code.value = "";
    if (action === "verify") email.value = "";
    await load();
  }
}
async function action(n, verb) {
  const r = await props.perform(`/notifications/${n.id}/${verb}`, "POST", {
    version: n.version,
  });
  if (r) await load();
}
function turn(n) {
  page.value = n;
  load();
}
function eventName(event) {
  return (
    {
      CONFIRM: t("订单确认", "Booking confirmed"),
      ASSIGN: t("照护排期调整", "Assignment changed"),
      START: t("照护开始", "Care started"),
      CHECK_OUT: t("宠物已接回", "Pet collected"),
      FINISH: t("交接待确认", "Handover ready"),
      ACCEPT: t("主人确认完成", "Owner confirmed"),
      DISPUTE: t("交接存在争议", "Handover disputed"),
      RESPOND: t("照护方已回复", "Provider responded"),
      CANCEL: t("订单取消", "Booking cancelled"),
      DUE: t("今日照护待办", "Care due today"),
      UNASSIGNED: t("尚未安排人员", "Staff needed"),
      OVERDUE: t("超期未接回", "Collection overdue"),
      TOMORROW: t("明日预约", "Booking tomorrow"),
    }[event] || event
  );
}
onMounted(load);
</script>
<template>
  <section>
    <p v-if="loading" class="loading">{{ t("正在读取…", "Loading…") }}</p>
    <template v-if="preferences"
      ><div class="settings-layout">
        <section class="panel">
          <h2>{{ t("提醒设置", "Reminder preferences") }}</h2>
          <form @submit.prevent="save">
            <label class="check-label"
              ><input
                v-model="preferences.remindersEnabled"
                type="checkbox"
              />{{
                t("每日待办与异常提醒", "Daily care and exception reminders")
              }}</label
            ><label
              >{{ t("提醒时间", "Reminder time") }} ({{
                preferences.zone || "Asia/Shanghai"
              }})<select v-model="preferences.reminderHour">
                <option v-for="hour in 24" :key="hour" :value="hour - 1">
                  {{ String(hour - 1).padStart(2, "0") }}:00
                </option>
              </select></label
            ><label
              >{{ t("邮件语言", "Email language")
              }}<select v-model="preferences.language">
                <option value="zh">中文</option>
                <option value="en">English</option>
              </select></label
            ><label class="check-label"
              ><input
                v-model="preferences.emailEnabled"
                type="checkbox"
                :disabled="!preferences.mailAvailable || !preferences.email"
              />{{ t("同时发送邮件", "Also send email") }}</label
            >
            <p class="hint">
              {{
                t(
                  "业务状态消息一直保存在站内；每日提醒可关闭。邮件不含私有照护资料，发送状态表示SMTP接受，不保证进入收件箱。",
                  "Business status messages remain in the inbox; daily reminders can be disabled. Emails omit private care details. Sent means SMTP acceptance, not guaranteed inbox delivery.",
                )
              }}
            </p>
            <button class="primary" :disabled="pending">
              {{ t("保存提醒设置", "Save reminder preferences") }}
            </button>
          </form>
        </section>
        <section class="panel">
          <h2>{{ t("接收邮箱", "Recipient email") }}</h2>
          <p>
            {{ preferences.email || t("尚未验证邮箱", "No verified email") }}
          </p>
          <p v-if="!preferences.mailAvailable" class="hint">
            {{
              t(
                "邮件服务尚未配置，站内消息可用。",
                "Email service is not configured. In-app messages are available.",
              )
            }}
          </p>
          <template v-else
            ><form @submit.prevent="verify('request')">
              <label
                >{{ t("验证本人邮箱", "Verify your email")
                }}<input
                  v-model="email"
                  type="email"
                  required
                  maxlength="254"
                  autocomplete="off" /></label
              ><button class="secondary" :disabled="pending">
                {{ t("发送验证邮件", "Send verification email") }}
              </button>
            </form>
            <form
              v-if="preferences.pendingVerification"
              @submit.prevent="verify('verify')"
            >
              <label
                >{{
                  t(
                    "六位验证码（15分钟有效）",
                    "Six-digit code (valid for 15 minutes)",
                  )
                }}<input
                  v-model="code"
                  inputmode="numeric"
                  pattern="[0-9]{6}"
                  minlength="6"
                  maxlength="6"
                  autocomplete="off"
                  required /></label
              ><button class="secondary" :disabled="pending">
                {{ t("确认邮箱", "Confirm email") }}
              </button>
            </form></template
          >
          <p class="hint">
            {{
              t(
                "验证后需主动开启邮件；短信和微信模板提醒尚未接入。",
                "Enable emails explicitly after verification. SMS and WeChat template notifications are not integrated.",
              )
            }}
          </p>
        </section>
      </div></template
    >
    <section v-if="inbox" class="panel">
      <header class="section-heading">
        <h2>{{ t("站内消息", "Inbox") }}</h2>
        <span>{{ t("未读", "Unread") }} {{ inbox.unread }}</span
        ><button class="secondary compact" :disabled="loading" @click="load">
          {{ t("刷新", "Refresh") }}
        </button>
      </header>
      <p v-if="!inbox.items.length" class="muted">
        {{ t("暂无消息。", "No messages yet.") }}
      </p>
      <article v-for="n in inbox.items" :key="n.id" class="notification-row">
        <div>
          <strong>{{ eventName(n.event) }}</strong>
          <p>
            <button class="text-button" @click="emit('open-order', n.orderId)">
              {{ n.reference }}
            </button>
            · <time>{{ new Date(n.createdAt).toLocaleString() }}</time>
          </p>
          <p class="muted">
            {{ t("邮件状态", "Email status") }}:
            {{ stateName(n.deliveryStatus) }} · {{ t("尝试次数", "Attempts") }}
            {{ n.attempts }}
          </p>
          <p v-if="n.failureCode === 'SMTP_FAILURE'" class="hint">
            {{
              t(
                "邮件服务器未接受，站内消息已保留。",
                "SMTP did not accept delivery; this in-app message is retained.",
              )
            }}
          </p>
        </div>
        <div class="row-actions">
          <button
            v-if="!n.readAt"
            class="secondary compact"
            :disabled="pending"
            @click="action(n, 'read')"
          >
            {{ t("标为已读", "Mark read") }}</button
          ><button
            v-if="n.deliveryStatus === 'FAILED' && n.attempts < 10"
            class="secondary compact"
            :disabled="pending"
            @click="action(n, 'retry')"
          >
            {{ t("重试邮件", "Retry email") }}
          </button>
        </div>
      </article>
      <footer class="pagination">
        <button
          class="secondary compact"
          :disabled="page <= 1 || loading"
          @click="turn(page - 1)"
        >
          {{ t("上一页", "Previous") }}</button
        ><span>{{ page }} / {{ inbox.pages }} · {{ inbox.total }}</span
        ><button
          class="secondary compact"
          :disabled="page >= inbox.pages || loading"
          @click="turn(page + 1)"
        >
          {{ t("下一页", "Next") }}
        </button>
      </footer>
    </section>
  </section>
</template>
