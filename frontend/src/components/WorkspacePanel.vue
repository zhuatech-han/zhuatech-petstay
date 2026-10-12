<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import {
  Plus,
  Pencil,
  X,
  ArrowRight,
  CalendarDays,
  PawPrint,
  Search,
  Trash2,
} from "@lucide/vue";
import { toMinor } from "../currency.js";
import { api } from "../api.js";
import {
  t,
  money,
  kindName,
  stateName,
  windowName,
  speciesName,
  cloneRecord,
} from "../ui.js";
const props = defineProps({
  section: { type: String, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "open-order", "session-ended"]);
const rows = ref([]),
  pets = ref([]),
  options = ref({ today: "", species: [] }),
  profile = ref(null),
  provider = ref(null),
  availability = ref([]),
  opportunities = ref([]),
  offers = ref([]),
  ready = ref(false),
  editing = ref(null),
  detail = ref(null),
  actionForm = ref(null),
  q = ref(""),
  status = ref(""),
  page = ref(1),
  providerTab = ref("profile");
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      JSON.stringify(r).toLowerCase().includes(q.value.toLowerCase()) &&
      (!status.value || r.status === status.value),
  ),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 12, page.value * 12),
);
const offerPage = ref(1),
  offerQuery = ref(""),
  offerStatus = ref("");
const filteredOffers = computed(() =>
  offers.value.filter(
    (row) =>
      String(row.requestId).includes(
        offerQuery.value.trim().replace(/^#/, ""),
      ) &&
      (!offerStatus.value || row.status === offerStatus.value),
  ),
);
const visibleOffers = computed(() =>
  filteredOffers.value.slice((offerPage.value - 1) * 12, offerPage.value * 12),
);
watch([offerQuery, offerStatus], () => {
  offerPage.value = 1;
});
watch(
  () => filteredOffers.value.length,
  (length) => {
    offerPage.value = Math.min(
      offerPage.value,
      Math.max(1, Math.ceil(length / 12)),
    );
  },
);
const passwordForm = ref({ oldPassword: "", newPassword: "" });
async function changePassword() {
  const r = await props.perform("/auth/password", "POST", passwordForm.value);
  if (r) {
    passwordForm.value = { oldPassword: "", newPassword: "" };
    emit("session-ended");
  }
}
const tomorrow = (d, n = 1) => {
  const x = new Date(d + "T12:00:00Z");
  x.setUTCDate(x.getUTCDate() + n);
  return x.toISOString().slice(0, 10);
};
/** 各工作区读取本人资料；报价机会由服务端按真实范围计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  ready.value = false;
  try {
    options.value = await api("/options");
    if (props.section === "profile") profile.value = await api("/profile");
    else if (props.section === "provider") {
      const r = await api("/provider");
      provider.value = r.provider;
      availability.value = r.availability;
      if (provider.value) {
        offers.value = await api("/my-offers");
        opportunities.value =
          provider.value.status === "APPROVED"
            ? await api("/opportunities")
            : [];
      }
    } else {
      rows.value = await api("/" + props.section);
      if (props.section === "requests") pets.value = await api("/pets");
    }
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
function edit(type, row) {
  if (row) {
    const value = cloneRecord(row);
    if (type === "provider") {
      value.boardingPriceYuan = (value.boardingPriceMinor / 100).toFixed(2);
      value.visitPriceYuan = (value.visitPriceMinor / 100).toFixed(2);
    }
    editing.value = { type, value };
    return;
  }
  const day = options.value.today;
  const value =
    type === "pets"
      ? { name: "", species: "CAT", weightKg: "", careNotes: "", enabled: true }
      : type === "requests"
        ? {
            petId: pets.value.find((p) => p.enabled)?.id || "",
            kind: "HOME_VISIT",
            visitWindow: "AM",
            startsOn: day,
            endsOn: tomorrow(day),
            city: "",
            district: "",
            title: "",
            publicNote: "",
            address: "",
            careNotes: "",
          }
        : type === "availability"
          ? {
              kind: provider.value?.boarding ? "BOARDING" : "HOME_VISIT",
              visitWindow: "AM",
              startsOn: day,
              endsOn: tomorrow(day, 7),
            }
          : {
              displayName: "",
              city: "",
              district: "",
              address: "",
              intro: "",
              species: "BOTH",
              maxWeightKg: 40,
              boarding: true,
              homeVisit: true,
              boardingCapacity: 1,
              visitCapacity: 1,
              boardingPriceYuan: "0.00",
              visitPriceYuan: "0.00",
            };
  editing.value = { type, value };
}
async function save() {
  const { type, value } = editing.value;
  if (type === "provider") {
    try {
      value.boardingPriceMinor = toMinor(value.boardingPriceYuan);
      value.visitPriceMinor = toMinor(value.visitPriceYuan);
    } catch (e) {
      emit("error", e);
      return;
    }
  }
  const path =
    type === "provider"
      ? "/provider"
      : `/${type}${value.id ? "/" + value.id : ""}`;
  const result = await props.perform(
    path,
    type !== "provider" && value.id ? "PUT" : "POST",
    value,
  );
  if (result) {
    editing.value = null;
    await load();
  }
}
async function saveProfile() {
  const r = await props.perform("/profile", "POST", profile.value);
  if (r) profile.value = r;
}
async function openRequest(row) {
  try {
    detail.value = await api("/requests/" + row.id);
  } catch (e) {
    emit("error", e);
  }
}
async function requestAction(row, action) {
  const r = await props.perform(`/requests/${row.id}/${action}`, "POST", {
    version: row.version,
  });
  if (r) {
    await load();
    if (detail.value) await openRequest(r);
  }
}
function confirm(type, row, extra = {}) {
  actionForm.value = { type, row, extra, note: "" };
}
async function submitAction() {
  const a = actionForm.value;
  let path, body;
  if (a.type === "select") {
    path = `/requests/${a.row.requestId}/select`;
    body = {
      version: detail.value.request.version,
      offerId: a.row.id,
      offerVersion: a.row.version,
      note: a.note,
    };
  } else if (a.type === "propose") {
    path = `/requests/${a.row.id}/propose`;
    body = { version: a.row.version, note: a.note };
  } else if (a.type === "withdraw") {
    path = `/offers/${a.row.id}/withdraw`;
    body = { version: a.row.version };
  } else {
    path = `/${a.type}/${a.row.id}`;
    body = { version: a.row.version };
  }
  const r = await props.perform(
    path,
    ["pets", "availability"].includes(a.type) ? "DELETE" : "POST",
    body,
  );
  if (r) {
    actionForm.value = null;
    if (a.type === "select") {
      detail.value = null;
      emit("open-order", r.id);
    } else await load();
  }
}
const ownOffer = (r) =>
  offers.value.find(
    (o) =>
      o.requestId === r.id &&
      o.status === "PROPOSED" &&
      Date.parse(o.expiresAt) > Date.now(),
  );
onMounted(load);
</script>
<template>
  <section>
    <p v-if="!ready" class="loading">{{ t("正在读取…", "Loading…") }}</p>
    <template v-else-if="section === 'profile' && profile"
      ><div class="panel narrow">
        <h2>{{ t("我的联系资料", "My contact details") }}</h2>
        <p class="hint">
          {{
            t(
              "仅向已确认订单双方开放。修改不会覆盖旧订单确认时的联系资料。",
              "Shared only with confirmed booking participants. Updates do not overwrite old booking details.",
            )
          }}
        </p>
        <form @submit.prevent="saveProfile">
          <div class="form-grid">
            <label
              >{{ t("联系人", "Contact name")
              }}<input
                v-model="profile.contactName"
                required
                maxlength="120" /></label
            ><label
              >{{ t("联系方式", "Contact details")
              }}<input
                v-model="profile.contact"
                required
                maxlength="160"
                :placeholder="
                  t('电话或约定的联系渠道', 'Phone or agreed contact channel')
                " /></label
            ><label
              >{{ t("所在城市", "City")
              }}<input v-model="profile.city" required maxlength="80" /></label
            ><label
              >{{ t("所在地区", "District")
              }}<input v-model="profile.district" required maxlength="80"
            /></label>
          </div>
          <button class="primary" :disabled="pending">
            {{ t("保存联系资料", "Save contact details") }}
          </button>
        </form>
      </div>
      <div class="panel narrow">
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <form @submit.prevent="changePassword">
          <div class="form-grid">
            <label
              >{{ t("当前密码", "Current password")
              }}<input
                v-model="passwordForm.oldPassword"
                type="password"
                required
                autocomplete="new-password" /></label
            ><label
              >{{ t("新密码", "New password")
              }}<input
                v-model="passwordForm.newPassword"
                type="password"
                required
                autocomplete="new-password"
                maxlength="128"
            /></label>
          </div>
          <p class="hint">
            {{
              t(
                "修改后需重新登录。密码需12–72个UTF-8字节，含大小写字母和数字。",
                "Sign in again after changing your password. Use 12–72 UTF-8 bytes with uppercase, lowercase and a number.",
              )
            }}
          </p>
          <button class="secondary" :disabled="pending">
            {{ t("保存新密码", "Save new password") }}
          </button>
        </form>
      </div></template
    >
    <template v-else-if="ready && ['pets', 'requests'].includes(section)"
      ><div class="list-tools">
        <label class="inline-search"
          ><Search :size="17" /><input
            v-model="q"
            :placeholder="t('搜索记录', 'Search records')"
            @input="page = 1" /></label
        ><select
          v-if="section === 'requests'"
          v-model="status"
          :aria-label="t('状态筛选', 'Status filter')"
          @change="page = 1"
        >
          <option value="">{{ t("全部状态", "All states") }}</option>
          <option
            v-for="s in ['DRAFT', 'OPEN', 'BOOKED', 'CANCELLED']"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option></select
        ><button class="primary" :disabled="pending" @click="edit(section)">
          <Plus :size="17" />{{
            section === "pets"
              ? t("添加宠物", "Add pet")
              : t("新建照护需求", "New care request")
          }}
        </button>
      </div>
      <div v-if="!filtered.length" class="empty-state">
        <PawPrint :size="38" />
        <h3>
          {{
            section === "pets"
              ? t("建立它的照护档案", "Create your pet's care profile")
              : t("还没有照护需求", "No care requests yet")
          }}
        </h3>
        <p>
          {{
            section === "pets"
              ? t(
                  "宠物档案只对你和已确认订单双方开放。",
                  "Pet details are private to you and confirmed service participants.",
                )
              : t(
                  "先完善联系资料和宠物，再发布寄养或上门照护需求。",
                  "Complete your contact and pet details before posting a request.",
                )
          }}
        </p>
      </div>
      <div v-if="section === 'pets'" class="pet-grid">
        <article v-for="row in visible" :key="row.id" class="panel pet-card">
          <header>
            <div class="pet-avatar"><PawPrint :size="27" /></div>
            <div>
              <h3>{{ row.name }}</h3>
              <p class="muted">
                {{ speciesName(row.species) }} · {{ row.weightKg }}kg
              </p>
            </div>
            <span class="badge">{{
              row.enabled ? t("启用", "Active") : t("停用", "Disabled")
            }}</span>
          </header>
          <p class="pre-wrap">
            {{
              row.careNotes ||
              t("暂无补充照护要求。", "No additional care instructions.")
            }}
          </p>
          <div class="row-actions">
            <button
              class="secondary"
              :disabled="pending"
              @click="edit('pets', row)"
            >
              <Pencil :size="15" />{{ t("编辑档案", "Edit profile") }}</button
            ><button
              class="quiet"
              :disabled="pending"
              @click="confirm('pets', row)"
            >
              <Trash2 :size="15" />{{ t("删除误建", "Delete unused") }}
            </button>
          </div>
        </article>
      </div>
      <div v-else class="request-list">
        <article
          v-for="row in visible"
          :key="row.id"
          class="panel request-card"
        >
          <header>
            <div>
              <p class="eyebrow">
                {{ kindName(row.kind) }} · {{ row.city }} / {{ row.district }}
              </p>
              <h3>{{ row.title }}</h3>
            </div>
            <span class="badge" :class="row.status.toLowerCase()">{{
              stateName(row.status)
            }}</span>
          </header>
          <p class="date-line">
            <CalendarDays :size="17" />{{ row.startsOn }} → {{ row.endsOn }} ·
            {{ windowName(row.visitWindow) }}
          </p>
          <p class="muted">{{ row.publicNote }}</p>
          <footer>
            <div class="row-actions">
              <template v-if="row.status === 'DRAFT'"
                ><button
                  class="secondary"
                  :disabled="pending"
                  @click="edit('requests', row)"
                >
                  {{ t("编辑", "Edit") }}</button
                ><button
                  class="primary compact"
                  :disabled="pending"
                  @click="requestAction(row, 'publish')"
                >
                  {{ t("发布匹配", "Publish") }}
                </button></template
              ><button
                v-if="['DRAFT', 'OPEN'].includes(row.status)"
                class="quiet"
                :disabled="pending"
                @click="requestAction(row, 'cancel')"
              >
                {{ t("关闭需求", "Close request") }}
              </button>
            </div>
            <button class="text-button" @click="openRequest(row)">
              {{ t("详情与报价", "Details & offers") }}<ArrowRight :size="17" />
            </button>
          </footer>
        </article>
      </div>
      <div v-if="filtered.length > 12" class="pagination">
        <button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><span>{{ page }} / {{ Math.ceil(filtered.length / 12) }}</span
        ><button :disabled="page * 12 >= filtered.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </div>
    </template>
    <template v-else-if="ready && section === 'provider'"
      ><div class="tabs">
        <button
          :class="{ active: providerTab === 'profile' }"
          @click="providerTab = 'profile'"
        >
          {{ t("服务资料与档期", "Profile & availability") }}</button
        ><button
          :class="{ active: providerTab === 'opportunities' }"
          @click="providerTab = 'opportunities'"
        >
          {{ t("匹配需求", "Matching requests") }}</button
        ><button
          :class="{ active: providerTab === 'offers' }"
          @click="providerTab = 'offers'"
        >
          {{ t("我的报价", "My offers") }}
        </button>
      </div>
      <template v-if="providerTab === 'profile'"
        ><div class="panel">
          <header class="section-heading">
            <div>
              <h2>
                {{
                  provider?.displayName ||
                  t("申请提供照护服务", "Apply to provide care")
                }}
              </h2>
              <p class="muted">
                {{
                  t(
                    "会员可同时作为宠物主与照护者。服务资料经审核后进入公开目录。",
                    "Members may be both owners and providers. Reviewed profiles appear in the directory.",
                  )
                }}
              </p>
            </div>
            <span v-if="provider" class="badge">{{
              stateName(provider.status)
            }}</span>
          </header>
          <template v-if="provider"
            ><p class="pre-wrap">{{ provider.intro }}</p>
            <p>
              {{ provider.city }} · {{ provider.district }} ·
              {{ speciesName(provider.species) }} · ≤{{
                provider.maxWeightKg
              }}kg
            </p>
            <div class="prices">
              <span v-if="provider.boarding"
                >{{ kindName("BOARDING") }}
                {{ money(provider.boardingPriceMinor) }} /
                {{ t("晚", "night") }}</span
              ><span v-if="provider.homeVisit"
                >{{ kindName("HOME_VISIT") }}
                {{ money(provider.visitPriceMinor) }} /
                {{ t("次", "visit") }}</span
              >
            </div>
            <p v-if="provider.reviewNote" class="hint">
              {{ t("审核说明：", "Review note: ") }}{{ provider.reviewNote }}
            </p></template
          ><button
            class="primary"
            :disabled="pending"
            @click="edit('provider', provider)"
          >
            {{
              provider
                ? t("更新并重新提交审核", "Update and resubmit")
                : t("填写服务资料", "Submit service profile")
            }}
          </button>
        </div>
        <section v-if="provider" class="panel">
          <header class="section-heading">
            <div>
              <h2>{{ t("已公布档期", "Published availability") }}</h2>
              <p class="hint">
                {{
                  t(
                    "上门照护按所选时段逐日计次；寄养按日期逐晚占用容量。",
                    "Home visits use one fixed window per day; boarding reserves nightly capacity.",
                  )
                }}
              </p>
            </div>
            <button
              class="secondary"
              :disabled="pending"
              @click="edit('availability')"
            >
              <Plus :size="16" />{{ t("公布档期", "Add availability") }}
            </button>
          </header>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{{ t("类型", "Type") }}</th>
                  <th>{{ t("起止日期", "Dates") }}</th>
                  <th>{{ t("上门时段", "Visit slot") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in availability" :key="row.id">
                  <td>{{ kindName(row.kind) }}</td>
                  <td>{{ row.startsOn }} → {{ row.endsOn }}</td>
                  <td>{{ windowName(row.visitWindow) }}</td>
                  <td>
                    <button
                      class="text-button"
                      :disabled="pending"
                      @click="edit('availability', row)"
                    >
                      {{ t("编辑", "Edit") }}</button
                    ><button
                      class="text-button danger"
                      :disabled="pending"
                      @click="confirm('availability', row)"
                    >
                      {{ t("移除", "Remove") }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!availability.length">
                  <td colspan="4">
                    {{ t("尚未公布档期。", "No availability published.") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section></template
      >
      <template v-else-if="providerTab === 'opportunities'"
        ><div v-if="!opportunities.length" class="empty-state">
          <CalendarDays :size="38" />
          <h3>{{ t("暂无可申请需求", "No matching requests") }}</h3>
          <p>
            {{
              t(
                "请确认服务资料已审核，并公布相应区域、类型和日期档期。",
                "Ensure your profile is approved and matching availability is published.",
              )
            }}
          </p>
        </div>
        <article
          v-for="row in opportunities"
          :key="row.id"
          class="panel request-card"
        >
          <header>
            <div>
              <p class="eyebrow">
                {{ kindName(row.kind) }} · {{ row.city }} / {{ row.district }}
              </p>
              <h3>{{ row.title }}</h3>
            </div>
            <span class="badge"
              >{{ speciesName(row.species) }} · {{ row.weightKg }}kg</span
            >
          </header>
          <p>
            {{ row.startsOn }} → {{ row.endsOn }} ·
            {{ windowName(row.visitWindow) }}
          </p>
          <p class="pre-wrap">{{ row.publicNote }}</p>
          <p class="hint">
            {{
              t(
                "报价按当前服务单价与次数计算并保留，48小时有效。确认后开放详细交接资料。",
                "Offer uses current service rates and dates, valid for 48 hours. Private handover details follow confirmation.",
              )
            }}
          </p>
          <button
            class="primary"
            :disabled="pending || !!ownOffer(row)"
            @click="confirm('propose', row)"
          >
            {{
              ownOffer(row)
                ? t("已提交有效报价", "Offer submitted")
                : t("申请并报价", "Apply and quote")
            }}
          </button>
        </article></template
      >
      <template v-else>
        <div class="list-tools">
          <input
            v-model="offerQuery"
            maxlength="30"
            :placeholder="t('搜索需求编号', 'Search request number')"
            :aria-label="t('搜索需求编号', 'Search request number')"
          />
          <select
            v-model="offerStatus"
            :aria-label="t('报价状态', 'Offer status')"
          >
            <option value="">{{ t("全部状态", "All states") }}</option>
            <option
              v-for="state in [
                'PROPOSED',
                'SELECTED',
                'NOT_SELECTED',
                'WITHDRAWN',
                'EXPIRED',
              ]"
              :key="state"
              :value="state"
            >
              {{ stateName(state) }}
            </option>
          </select>
          <span>{{ filteredOffers.length }} {{ t("条报价", "offers") }}</span>
        </div>
        <div class="table-scroll panel">
          <table>
            <thead>
              <tr>
                <th>{{ t("需求编号", "Request") }}</th>
                <th>{{ t("固定报价", "Fixed quote") }}</th>
                <th>{{ t("期限", "Expires") }}</th>
                <th>{{ t("状态", "State") }}</th>
                <th>{{ t("操作", "Actions") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in visibleOffers" :key="row.id">
                <td>#{{ row.requestId }}</td>
                <td>
                  {{ money(row.totalMinor) }} · {{ row.units
                  }}{{ t("单位", " units") }}
                </td>
                <td>{{ new Date(row.expiresAt).toLocaleString() }}</td>
                <td>
                  <span class="badge">{{ stateName(row.status) }}</span>
                </td>
                <td>
                  <button
                    v-if="row.status === 'PROPOSED'"
                    class="text-button"
                    :disabled="pending"
                    @click="confirm('withdraw', row)"
                  >
                    {{ t("撤回", "Withdraw") }}
                  </button>
                </td>
              </tr>
              <tr v-if="!filteredOffers.length">
                <td colspan="5">{{ t("暂无报价。", "No offers yet.") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="filteredOffers.length > 12" class="pagination">
          <button :disabled="offerPage === 1" @click="offerPage--">
            {{ t("上一页", "Previous") }}
          </button>
          <span
            >{{ offerPage }} / {{ Math.ceil(filteredOffers.length / 12) }}</span
          >
          <button
            :disabled="offerPage * 12 >= filteredOffers.length"
            @click="offerPage++"
          >
            {{ t("下一页", "Next") }}
          </button>
        </div>
      </template>
    </template>
    <div v-if="editing" class="modal-backdrop" @click.self="editing = null">
      <section
        class="dialog wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('编辑记录', 'Edit record')"
      >
        <header>
          <h2>
            {{
              editing.type === "pets"
                ? t("宠物档案", "Pet profile")
                : editing.type === "requests"
                  ? t("照护需求", "Care request")
                  : editing.type === "availability"
                    ? t("服务档期", "Availability")
                    : t("照护服务资料", "Service profile")
            }}
          </h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="editing = null"
          >
            <X :size="20" />
          </button>
        </header>
        <form @submit.prevent="save">
          <div class="form-grid">
            <template v-if="editing.type === 'pets'"
              ><label
                >{{ t("宠物名字", "Pet name")
                }}<input
                  v-model="editing.value.name"
                  required
                  maxlength="120" /></label
              ><label
                >{{ t("物种", "Species")
                }}<select v-model="editing.value.species">
                  <option
                    v-for="s in options.species"
                    :key="s.code"
                    :value="s.code"
                  >
                    {{ s.name }} / {{ s.nameEn }}
                  </option>
                </select></label
              ><label
                >{{ t("体重（kg）", "Weight (kg)")
                }}<input
                  v-model="editing.value.weightKg"
                  type="number"
                  min="0.01"
                  max="200"
                  step="0.01"
                  required /></label
              ><label class="check-label"
                ><input v-model="editing.value.enabled" type="checkbox" />{{
                  t("启用宠物档案", "Active pet")
                }}</label
              ><label class="span-two"
                >{{ t("私有照护要求", "Private care instructions")
                }}<textarea
                  v-model="editing.value.careNotes"
                  maxlength="1800"
                  rows="4"
                /></label
            ></template>
            <template v-else-if="editing.type === 'requests'"
              ><label class="span-two"
                >{{
                  t(
                    "需求标题（向匹配照护者展示）",
                    "Request title (shown to matching providers)",
                  )
                }}<input
                  v-model="editing.value.title"
                  required
                  maxlength="120" /></label
              ><label
                >{{ t("我的宠物", "My pet")
                }}<select v-model="editing.value.petId" required>
                  <option value="">{{ t("请选择", "Choose") }}</option>
                  <option
                    v-for="p in pets.filter((x) => x.enabled)"
                    :key="p.id"
                    :value="p.id"
                  >
                    {{ p.name }} · {{ speciesName(p.species) }}
                  </option>
                </select></label
              ><label
                >{{ t("服务类型", "Care type")
                }}<select v-model="editing.value.kind">
                  <option value="HOME_VISIT">
                    {{ kindName("HOME_VISIT") }}
                  </option>
                  <option value="BOARDING">{{ kindName("BOARDING") }}</option>
                </select></label
              ><label
                >{{ t("城市", "City")
                }}<input
                  v-model="editing.value.city"
                  required
                  maxlength="80" /></label
              ><label
                >{{ t("地区", "District")
                }}<input
                  v-model="editing.value.district"
                  required
                  maxlength="80" /></label
            ></template>
            <template v-else-if="editing.type === 'provider'"
              ><label class="span-two"
                >{{ t("公开服务名称", "Public service name")
                }}<input
                  v-model="editing.value.displayName"
                  required
                  maxlength="120" /></label
              ><label
                >{{ t("服务城市", "Service city")
                }}<input
                  v-model="editing.value.city"
                  required
                  maxlength="80" /></label
              ><label
                >{{ t("服务地区", "Service district")
                }}<input
                  v-model="editing.value.district"
                  required
                  maxlength="80" /></label
              ><label
                >{{ t("接待物种", "Accepted species")
                }}<select v-model="editing.value.species">
                  <option value="BOTH">{{ t("猫与犬", "Cats & dogs") }}</option>
                  <option value="CAT">{{ t("猫", "Cat") }}</option>
                  <option value="DOG">{{ t("犬", "Dog") }}</option>
                </select></label
              ><label
                >{{ t("最大体重（kg）", "Maximum weight (kg)")
                }}<input
                  v-model="editing.value.maxWeightKg"
                  type="number"
                  min="0.01"
                  max="200"
                  step="0.01"
                  required /></label
              ><label class="check-label"
                ><input v-model="editing.value.boarding" type="checkbox" />{{
                  t("提供寄养", "Provide boarding")
                }}</label
              ><label class="check-label"
                ><input v-model="editing.value.homeVisit" type="checkbox" />{{
                  t("提供上门照护", "Provide home visits")
                }}</label
              ><label
                >{{ t("每晚寄养容量", "Nightly boarding capacity")
                }}<input
                  v-model="editing.value.boardingCapacity"
                  type="number"
                  min="1"
                  max="20"
                  required /></label
              ><label
                >{{ t("每时段上门名额", "Visits per time slot")
                }}<input
                  v-model="editing.value.visitCapacity"
                  type="number"
                  min="1"
                  max="10"
                  required /></label
              ><label
                >{{ t("寄养单价（元/晚）", "Boarding price (CNY/night)")
                }}<input
                  v-model="editing.value.boardingPriceYuan"
                  type="number"
                  min="0"
                  max="10000"
                  step="0.01"
                  required /></label
              ><label
                >{{ t("上门单价（元/次）", "Visit price (CNY/visit)")
                }}<input
                  v-model="editing.value.visitPriceYuan"
                  type="number"
                  min="0"
                  max="10000"
                  step="0.01"
                  required /></label
              ><label class="span-two"
                >{{
                  t(
                    "寄养详细地点（确认后开放）",
                    "Boarding address (after confirmation)",
                  )
                }}<input
                  v-model="editing.value.address"
                  :required="editing.value.boarding"
                  maxlength="500" /></label
              ><label class="span-two"
                >{{ t("公开服务说明", "Public service description")
                }}<textarea
                  v-model="editing.value.intro"
                  required
                  maxlength="1200"
                  rows="4"
                /></label
            ></template>
            <template v-if="['requests', 'availability'].includes(editing.type)"
              ><label v-if="editing.type === 'availability'"
                >{{ t("服务类型", "Care type")
                }}<select v-model="editing.value.kind">
                  <option v-if="provider?.boarding" value="BOARDING">
                    {{ kindName("BOARDING") }}
                  </option>
                  <option v-if="provider?.homeVisit" value="HOME_VISIT">
                    {{ kindName("HOME_VISIT") }}
                  </option>
                </select></label
              ><label v-if="editing.value.kind === 'HOME_VISIT'"
                >{{ t("每日上门时段", "Daily visit slot")
                }}<select v-model="editing.value.visitWindow">
                  <option value="AM">{{ windowName("AM") }}</option>
                  <option value="PM">{{ windowName("PM") }}</option>
                  <option value="EVENING">{{ windowName("EVENING") }}</option>
                </select></label
              ><label
                >{{ t("开始日期（含）", "Start date (inclusive)")
                }}<input
                  v-model="editing.value.startsOn"
                  type="date"
                  :min="options.today"
                  required /></label
              ><label
                >{{ t("结束日期（不含）", "End date (exclusive)")
                }}<input
                  v-model="editing.value.endsOn"
                  type="date"
                  :min="editing.value.startsOn"
                  required /></label
            ></template>
            <template v-if="editing.type === 'requests'"
              ><label class="span-two"
                >{{ t("公开需求说明", "Public request note")
                }}<textarea
                  v-model="editing.value.publicNote"
                  maxlength="1000"
                  rows="3"
                  :placeholder="
                    t(
                      '不要在此填写详细地址或联系方式',
                      'Do not include exact addresses or contact details here',
                    )
                  "
                /></label
              ><label class="span-two"
                >{{
                  t(
                    "上门准确地址（确认后开放）",
                    "Exact visit address (after confirmation)",
                  )
                }}<input
                  v-model="editing.value.address"
                  :required="editing.value.kind === 'HOME_VISIT'"
                  maxlength="500" /></label
              ><label class="span-two"
                >{{
                  t(
                    "本次私有照护说明",
                    "Private care instructions for this request",
                  )
                }}<textarea
                  v-model="editing.value.careNotes"
                  maxlength="1800"
                  rows="3"
                />
              </label>
              <p class="hint span-two">
                {{
                  t(
                    "寄养最多30晚；上门最多14天、所选时段每天一次。先保存草稿，再发布。",
                    "Up to 30 boarding nights or 14 daily home visits in one selected window. Save a draft, then publish.",
                  )
                }}
              </p></template
            >
          </div>
          <footer class="dialog-footer">
            <button class="secondary" type="button" @click="editing = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存", "Save") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
    <div v-if="detail" class="modal-backdrop" @click.self="detail = null">
      <section
        class="dialog wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('需求详情与报价', 'Request and offers')"
      >
        <header>
          <div>
            <p class="eyebrow">
              {{ stateName(detail.request.status) }} · #{{ detail.request.id }}
            </p>
            <h2>{{ detail.request.title }}</h2>
          </div>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="detail = null"
          >
            <X :size="20" />
          </button>
        </header>
        <p>
          {{ kindName(detail.request.kind) }} · {{ detail.request.startsOn }} →
          {{ detail.request.endsOn }} ·
          {{ windowName(detail.request.visitWindow) }}
        </p>
        <p>{{ detail.request.city }} / {{ detail.request.district }}</p>
        <h3>{{ t("照护者报价", "Provider offers") }}</h3>
        <p v-if="!detail.offers.length" class="muted">
          {{
            t(
              "尚未收到报价。已审核且有匹配档期的照护者可申请。",
              "No offers yet. Approved providers with matching availability can apply.",
            )
          }}
        </p>
        <article v-for="o in detail.offers" :key="o.id" class="quote-card">
          <header>
            <strong>{{ o.providerName }}</strong
            ><span class="badge">{{ stateName(o.status) }}</span>
          </header>
          <p>
            {{ money(o.unitMinor) }} × {{ o.units }} =
            <strong>{{ money(o.totalMinor) }}</strong>
          </p>
          <p class="pre-wrap">{{ o.note }}</p>
          <p class="hint">
            {{ t("有效至：", "Valid until: ")
            }}{{ new Date(o.expiresAt).toLocaleString() }}
          </p>
          <button
            v-if="
              detail.request.status === 'OPEN' &&
              o.status === 'PROPOSED' &&
              Date.parse(o.expiresAt) > Date.now()
            "
            class="primary"
            :disabled="pending"
            @click="confirm('select', o)"
          >
            {{ t("选定并确认订单", "Select and confirm") }}
          </button>
        </article>
        <h3>{{ t("当前匹配的照护方", "Currently matching providers") }}</h3>
        <p class="hint">
          {{
            t(
              "按地区、物种、体重与公布档期筛选；仍需照护者提出报价。",
              "Filtered by region, species, weight and published availability. A provider offer is still required.",
            )
          }}
        </p>
        <div class="candidate-list">
          <div v-for="p in detail.candidates" :key="p.id">
            <strong>{{ p.displayName }}</strong
            ><span>{{ p.city }} · {{ p.district }}</span>
          </div>
          <p v-if="!detail.candidates.length" class="muted">
            {{
              t(
                "暂无符合全部条件的照护方。",
                "No providers match all requirements.",
              )
            }}
          </p>
        </div>
      </section>
    </div>
    <div v-if="actionForm" class="modal-backdrop">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('确认操作', 'Confirm action')"
      >
        <header>
          <h2>
            {{
              actionForm.type === "select"
                ? t("确认固定报价与服务", "Confirm fixed quote and service")
                : actionForm.type === "propose"
                  ? t("提出照护报价", "Submit care offer")
                  : t("确认操作", "Confirm action")
            }}
          </h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="actionForm = null"
          >
            <X :size="20" />
          </button>
        </header>
        <form @submit.prevent="submitAction">
          <template v-if="actionForm.type === 'select'"
            ><p>
              <strong
                >{{ actionForm.row.providerName }} ·
                {{ money(actionForm.row.totalMinor) }}</strong
              >
            </p>
            <p class="hint">
              {{
                t(
                  "确认后将生成订单并开放双方交接资料。请核对固定金额和日期。",
                  "Confirmation creates a booking and shares private handover details. Check the fixed total and dates.",
                )
              }}
            </p></template
          ><template v-else-if="actionForm.type === 'propose'"
            ><p>
              {{ kindName(actionForm.row.kind) }} ·
              {{ actionForm.row.startsOn }} → {{ actionForm.row.endsOn }}
            </p>
            <p>
              {{ t("单价：", "Unit price: ")
              }}{{
                money(
                  actionForm.row.kind === "BOARDING"
                    ? provider.boardingPriceMinor
                    : provider.visitPriceMinor,
                )
              }}
              /
              {{
                actionForm.row.kind === "BOARDING"
                  ? t("晚", "night")
                  : t("次", "visit")
              }}
            </p></template
          >
          <p v-else>
            {{
              t(
                actionForm.type === "withdraw"
                  ? "撤回后主人不能再选择这份报价，历史记录保留。"
                  : actionForm.type === "availability"
                    ? "删除这段可接时间；已有订单占用时不能删除。"
                    : "删除误建宠物档案；已有需求历史时不能删除。",
                actionForm.type === "withdraw"
                  ? "The owner can no longer select this offer. Its history is retained."
                  : actionForm.type === "availability"
                    ? "Remove this availability period. Periods used by bookings cannot be deleted."
                    : "Remove an unused pet profile. Profiles with request history cannot be deleted.",
              )
            }}
          </p>
          <label v-if="['select', 'propose'].includes(actionForm.type)"
            >{{ t("确认说明", "Confirmation note")
            }}<textarea
              v-model="actionForm.note"
              required
              maxlength="1000"
              rows="4"
            />
          </label>
          <footer class="dialog-footer">
            <button class="secondary" type="button" @click="actionForm = null">
              {{ t("返回", "Back") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("确认", "Confirm") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
