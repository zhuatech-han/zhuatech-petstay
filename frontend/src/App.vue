<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  PawPrint,
  Menu,
  X,
  ArrowRight,
  LogOut,
  Globe,
  ShieldCheck,
} from "@lucide/vue";
import { api, resetApi } from "./api.js";
import { t, language, errorText } from "./ui.js";
import MarketPanel from "./components/MarketPanel.vue";
import WorkspacePanel from "./components/WorkspacePanel.vue";
import OrderPanel from "./components/OrderPanel.vue";
import OperationsPanel from "./components/OperationsPanel.vue";
import AdminPanel from "./components/AdminPanel.vue";
const me = ref(null),
  view = ref("market"),
  pending = ref(false),
  notice = ref(""),
  error = ref(""),
  authOpen = ref(false),
  authMode = ref("login"),
  about = ref(false),
  mobileMenu = ref(false),
  orderId = ref(null);
const credentials = ref({ username: "", password: "", displayName: "" });
const mainViews = new Set(["market", "pets", "requests", "orders", "provider"]);
const nav = computed(
  () => me.value?.menus?.filter((m) => mainViews.has(m.code)) || [],
);
const opsNav = computed(
  () => me.value?.menus?.filter((m) => !mainViews.has(m.code)) || [],
);
const canPortal = computed(() => me.value?.permissions.includes("portal"));
const viewTitle = computed(
  () =>
    me.value?.menus?.find((m) => m.code === view.value)?.[
      language.value === "en" ? "nameEn" : "name"
    ] ||
    (view.value === "orders"
      ? t("照护订单", "Care orders")
      : t("我的资料", "My profile")),
);
/** 只显示真实服务端成功，不自动重放写操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  error.value = "";
  notice.value = "";
  try {
    const result = await api(path, { method, body });
    notice.value = t("已保存。", "Saved.");
    return result;
  } catch (e) {
    onError(e);
    return null;
  } finally {
    pending.value = false;
  }
}
function onError(e) {
  notice.value = "";
  error.value = errorText(e);
  if (e.status === 401) {
    me.value = null;
    orderId.value = null;
    view.value = "market";
    resetApi();
  }
}
function go(code) {
  mobileMenu.value = false;
  notice.value = "";
  error.value = "";
  if (code !== "market" && !me.value) {
    authOpen.value = true;
    return;
  }
  view.value = code;
  orderId.value = null;
  location.hash = code;
}
function openOrder(id) {
  orderId.value = id;
  view.value = "orders";
  location.hash = "order/" + id;
}
function requireAccount() {
  if (me.value && !me.value.permissions.includes("portal")) {
    error.value = t(
      "请使用会员账号发布需求。",
      "Use a member account to post requests.",
    );
    return;
  }
  if (!me.value) {
    authOpen.value = true;
    return;
  }
  go("requests");
}
function switchLanguage() {
  language.value = language.value === "zh" ? "en" : "zh";
  localStorage.setItem("petstay-language", language.value);
}
/** 登录后获取最新身份，不将密码或令牌持久化到浏览器。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function auth() {
  if (pending.value) return;
  pending.value = true;
  error.value = "";
  notice.value = "";
  try {
    if (authMode.value === "register")
      await api("/auth/register", { method: "POST", body: credentials.value });
    me.value = await api("/auth/login", {
      method: "POST",
      body: {
        username: credentials.value.username,
        password: credentials.value.password,
      },
    });
    resetApi();
    credentials.value = { username: "", password: "", displayName: "" };
    authOpen.value = false;
    view.value = me.value.permissions.includes("moderate")
      ? "moderate"
      : "market";
    notice.value = t("登录成功。", "Signed in.");
  } catch (e) {
    onError(e);
  } finally {
    pending.value = false;
  }
}
async function logout() {
  const r = await perform("/auth/logout", "POST", {});
  if (r) {
    me.value = null;
    orderId.value = null;
    view.value = "market";
    resetApi();
    notice.value = t("已退出。", "Signed out.");
  }
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    const hash = location.hash.slice(1);
    if (hash.startsWith("order/") && /^\d+$/.test(hash.slice(6)))
      openOrder(Number(hash.slice(6)));
    else if (me.value.menus.some((m) => m.code === hash) || hash === "profile")
      view.value = hash;
  } catch (e) {
    if (e.status !== 401) onError(e);
  }
});
</script>
<template>
  <div class="site-shell">
    <header class="site-header">
      <button class="brand" aria-label="PetStay home" @click="go('market')">
        <img src="/brand/logo.jpg" alt="知华科技" /><span
          ><strong>PetStay</strong
          ><small>{{
            t("寄养 · 上门照护", "Boarding · Home care")
          }}</small></span
        >
      </button>
      <nav class="desktop-nav" :aria-label="t('主要导航', 'Main navigation')">
        <button :class="{ active: view === 'market' }" @click="go('market')">
          {{ t("寻找照护", "Find care") }}</button
        ><template v-if="me"
          ><button
            v-for="m in nav.filter((x) => x.code !== 'market')"
            :key="m.code"
            :class="{ active: view === m.code }"
            @click="go(m.code)"
          >
            {{ language === "en" ? m.nameEn : m.name }}
          </button></template
        ><button v-else @click="authOpen = true">
          {{ t("成为照护者", "Provide care") }}
        </button>
      </nav>
      <div class="header-tools">
        <button
          class="icon-button"
          :aria-label="t('切换语言', 'Change language')"
          @click="switchLanguage"
        >
          <Globe :size="18" /><span>{{
            language === "en" ? "中文" : "EN"
          }}</span></button
        ><button class="quiet about-button" @click="about = true">
          {{ t("关于", "About") }}</button
        ><button
          v-if="me"
          class="account-name"
          @click="canPortal ? go('profile') : (about = true)"
        >
          {{ me.displayName }}</button
        ><button v-else class="primary compact" @click="authOpen = true">
          {{ t("登录 / 注册", "Sign in / Join") }}</button
        ><button
          v-if="me"
          class="icon-button"
          :aria-label="t('退出', 'Sign out')"
          :disabled="pending"
          @click="logout"
        >
          <LogOut :size="18" /></button
        ><button
          class="icon-button menu-toggle"
          :aria-label="t('打开导航', 'Open navigation')"
          @click="mobileMenu = !mobileMenu"
        >
          <Menu :size="21" />
        </button>
      </div>
    </header>
    <nav v-if="mobileMenu" class="mobile-nav">
      <button @click="go('market')">{{ t("寻找照护", "Find care") }}</button
      ><button
        v-for="m in nav.filter((x) => x.code !== 'market')"
        :key="m.code"
        @click="go(m.code)"
      >
        {{ language === "en" ? m.nameEn : m.name }}</button
      ><button v-if="me" @click="canPortal ? go('profile') : (about = true)">
        {{ t("我的资料", "My profile") }}
      </button>
    </nav>
    <nav
      v-if="me && opsNav.length"
      class="operations-nav"
      :aria-label="t('运营导航', 'Operations navigation')"
    >
      <ShieldCheck :size="16" /><button
        v-for="m in opsNav"
        :key="m.code"
        :class="{ active: view === m.code }"
        @click="go(m.code)"
      >
        {{ language === "en" ? m.nameEn : m.name }}
      </button>
    </nav>
    <div v-if="error" role="alert" class="message error">
      <span>{{ error }}</span
      ><button :aria-label="t('关闭', 'Close')" @click="error = ''">
        <X :size="16" />
      </button>
    </div>
    <div v-if="notice" role="status" class="message success">{{ notice }}</div>
    <main>
      <template v-if="view === 'market'"
        ><section class="hero">
          <div>
            <p class="eyebrow">
              {{
                t("给每一次托付留下一份记录", "A record of every care journey")
              }}
            </p>
            <h1>
              {{ t("为它安排合适的照护", "Find the right care for your pet") }}
            </h1>
            <p>
              {{
                t(
                  "寻找寄养与上门喂养服务，确认双方安排，在手机上查看照护记录。",
                  "Find boarding or home visits, agree on the service and follow care updates on your phone.",
                )
              }}
            </p>
            <button class="primary" @click="requireAccount">
              {{ t("发布照护需求", "Post a care request")
              }}<ArrowRight :size="18" />
            </button>
          </div>
          <div class="hero-mark" aria-hidden="true">
            <PawPrint :size="92" /><span>PetStay</span
            ><small>{{
              t(
                "寄养 / 上门照护 / 交接记录",
                "Boarding / Home visits / Care records",
              )
            }}</small>
          </div>
        </section>
        <MarketPanel
          :key="me?.id || 'guest'"
          :pending="pending"
          @error="onError"
          @request="requireAccount"
      /></template>
      <template v-else-if="me"
        ><header class="workspace-heading">
          <p class="eyebrow">PetStay / {{ t("工作空间", "Workspace") }}</p>
          <h1>{{ viewTitle }}</h1>
        </header>
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(view)"
          :section="view"
          :pending="pending"
          :perform="perform"
          @error="onError" /><OperationsPanel
          v-else-if="['moderate', 'reports', 'audit'].includes(view)"
          :key="view"
          :section="view"
          :pending="pending"
          :perform="perform"
          @error="onError"
          @open-order="openOrder" /><OrderPanel
          v-else-if="view === 'orders'"
          :key="me.id"
          :me="me"
          :initial-id="orderId"
          :pending="pending"
          :perform="perform"
          @error="onError" /><WorkspacePanel
          v-else-if="canPortal"
          :key="me.id + '-' + view"
          :section="view"
          :pending="pending"
          :perform="perform"
          @error="onError"
          @open-order="openOrder"
          @session-ended="
            me = null;
            view = 'market';
            resetApi();
            authOpen = true;
          "
      /></template>
    </main>
    <footer class="site-footer">
      <span>PetStay · {{ t("知华科技", "ZhiHua Technology") }}</span
      ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
        t("官网与商业咨询", "Website & commercial inquiries")
      }}</a
      ><button @click="about = true">
        {{ t("授权与平台说明", "License & platform information") }}
      </button>
    </footer>
    <div v-if="authOpen" class="modal-backdrop" @click.self="authOpen = false">
      <section
        class="dialog auth-dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('账号入口', 'Account access')"
      >
        <header>
          <div>
            <p class="eyebrow">PetStay</p>
            <h2>
              {{
                authMode === "login"
                  ? t("欢迎回来", "Welcome back")
                  : t("创建照护平台账号", "Join the care platform")
              }}
            </h2>
          </div>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="authOpen = false"
          >
            <X :size="20" />
          </button>
        </header>
        <div class="tabs">
          <button
            :class="{ active: authMode === 'login' }"
            @click="authMode = 'login'"
          >
            {{ t("登录", "Sign in") }}</button
          ><button
            :class="{ active: authMode === 'register' }"
            @click="authMode = 'register'"
          >
            {{ t("注册", "Register") }}
          </button>
        </div>
        <form @submit.prevent="auth">
          <label
            >{{ t("账号名", "Username")
            }}<input
              v-model="credentials.username"
              required
              maxlength="60"
              autocomplete="off"
              pattern="[a-zA-Z0-9_.\-]{3,60}" /></label
          ><label v-if="authMode === 'register'"
            >{{ t("昵称", "Display name")
            }}<input
              v-model="credentials.displayName"
              required
              maxlength="120"
              autocomplete="off" /></label
          ><label
            >{{ t("密码", "Password")
            }}<input
              v-model="credentials.password"
              type="password"
              required
              maxlength="128"
              autocomplete="new-password"
          /></label>
          <p v-if="authMode === 'register'" class="hint">
            {{
              t(
                "账号3–60位字母、数字或_.-；密码12–72个UTF-8字节，含大小写字母和数字。",
                "Username: 3–60 letters, digits or _.–; password: 12–72 UTF-8 bytes with upper/lowercase and a number.",
              )
            }}
          </p>
          <button class="primary full" :disabled="pending">
            {{
              pending
                ? t("处理中…", "Working…")
                : authMode === "login"
                  ? t("登录", "Sign in")
                  : t("注册并登录", "Register and sign in")
            }}
          </button>
        </form>
        <p class="hint">
          {{
            t(
              "宠物主与照护者共用会员账号；提供照护需另行提交服务资料。",
              "Owners and providers share a member account. Providers submit separate service details.",
            )
          }}
        </p>
      </section>
    </div>
    <div v-if="about" class="modal-backdrop" @click.self="about = false">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('平台说明', 'Platform information')"
      >
        <header>
          <h2>PetStay 1.0.0</h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="about = false"
          >
            <X :size="20" />
          </button>
        </header>
        <p>
          {{
            t(
              "知华科技（上海如静知华信息科技有限公司）",
              "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
            )
          }}
        </p>
        <p>
          {{
            t(
              "公开源码学习版，仅限个人学习、技术研究与非商业交流。未经书面授权不得商用。",
              "Source-available learning edition for personal study, research and non-commercial exchange. Commercial use requires written authorization.",
            )
          }}
        </p>
        <p>
          {{
            t(
              "服务资料审核不代表实名、保险或兽医资质认证。匹配结果按服务区域、宠物类型与已公布档期筛选，服务由双方确认。付款仅登记已核实的线下款项。",
              "Profile review is not identity, insurance or veterinary certification. Matching uses region, pet type and published availability, followed by agreement. Payments are records of verified offline transactions.",
            )
          }}
        </p>
        <p>
          {{
            t(
              "公众号可打开本手机网页。微信授权登录、微信支付和小程序发布状态见部署说明，不会在未配置时假装已接入。",
              "This mobile website can be opened from a public-account link. WeChat login, payment and mini-program release status depend on documented configuration and verification.",
            )
          }}
        </p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >https://www.zhuatech.cn/</a
        >
        <p>
          {{
            t(
              "商业授权与定制咨询微信：zhuatech、zhuatech2。",
              "Commercial inquiries: han@zhuatech.cn / jack@zhuatech.cn · WhatsApp +86 17521234993.",
            )
          }}
        </p>
      </section>
    </div>
  </div>
</template>
