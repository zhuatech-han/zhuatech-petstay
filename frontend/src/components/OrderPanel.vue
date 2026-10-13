<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import {
  ArrowLeft,
  ArrowRight,
  Download,
  Camera,
  CheckCircle2,
  X,
  Search,
  CalendarDays,
} from "@lucide/vue";
import { toMinor } from "../currency.js";
import { api, download } from "../api.js";
import {
  t,
  money,
  stateName,
  kindName,
  windowName,
  taskName,
  speciesName,
} from "../ui.js";
const props = defineProps({
  me: { type: Object, required: true },
  initialId: { type: Number, default: null },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const rows = ref([]),
  detail = ref(null),
  ready = ref(false),
  q = ref(""),
  status = ref(""),
  page = ref(1),
  form = ref(null),
  file = ref(null),
  photoCaption = ref("");
const total = ref(0),
  pages = ref(1),
  listError = ref(false),
  photoQuota = ref(null);
let listRequest = 0,
  detailRequest = 0;
/** 由服务端限定身份、搜索和分页范围；较晚返回的旧请求不能覆盖新页面。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function loadRows() {
  const seq = ++listRequest;
  ready.value = false;
  listError.value = false;
  try {
    const query = new URLSearchParams({
      page: String(page.value),
      size: "12",
      q: q.value,
      status: status.value,
    });
    const result = await api("/orders/page?" + query);
    if (seq !== listRequest) return;
    rows.value = result.items;
    total.value = result.total;
    pages.value = result.pages;
    page.value = result.page;
  } catch (e) {
    if (seq !== listRequest) return;
    listError.value = true;
    emit("error", e);
  } finally {
    if (seq === listRequest) ready.value = true;
  }
}
function search() {
  page.value = 1;
  load();
}
function turnPage(number) {
  page.value = number;
  load();
}
const isOwner = computed(
  () =>
    detail.value?.order.ownerId === props.me.id &&
    props.me.permissions.includes("portal"),
);
const isProvider = computed(
  () =>
    detail.value?.order.providerAccountId === props.me.id &&
    props.me.permissions.includes("portal"),
);
const isCoordinator = computed(() => detail.value?.canCoordinate);
const canTask = (task) => detail.value?.taskActionIds.includes(task.id);
const today = computed(
  () =>
    detail.value?.today ||
    new Intl.DateTimeFormat("en-CA", {
      timeZone: props.me.zone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date()),
);
async function load() {
  await loadRows();
}
function backToList() {
  detailRequest++;
  detail.value = null;
  form.value = null;
  file.value = null;
  photoCaption.value = "";
  photoQuota.value = null;
  location.hash = "orders";
}
async function open(id) {
  const seq = ++detailRequest;
  try {
    const [order, quota] = await Promise.all([
      api("/orders/" + id),
      api(`/orders/${id}/photo-allowance`),
    ]);
    if (seq !== detailRequest) return;
    if (detail.value?.order.id !== id) {
      form.value = null;
      file.value = null;
      photoCaption.value = "";
    }
    detail.value = order;
    photoQuota.value = quota;
    location.hash = "order/" + id;
  } catch (e) {
    if (seq === detailRequest) emit("error", e);
  }
}
function act(action, extra = {}) {
  form.value = { action, note: "", ...extra };
}
/** 每个状态动作带当前版本及实际说明；不存在自动成功或自动扣款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function submit() {
  const o = detail.value.order;
  const f = form.value;
  let path = `/orders/${o.id}/${f.action}`;
  let body = { version: o.version, note: f.note };
  if (f.action === "task" || f.action === "correct") {
    path = `/orders/${o.id}/tasks/${f.taskId}/${f.action === "correct" ? "correct" : "complete"}`;
    body.entryId = f.entryId;
  } else if (f.action === "money") {
    body = {
      ...body,
      kind: f.kind,
      amountMinor: toMinor(f.amountYuan),
      originalId: f.originalId,
      reference: f.reference,
    };
  } else if (f.action === "review") {
    body.rating = f.rating;
    body.comment = f.comment;
  }
  if (f.action === "check-out" || f.action === "start") {
    body.belongings = f.belongings || "";
    body.recipient = f.recipient || "";
  }
  const r = await props.perform(path, "POST", body);
  if (r) {
    form.value = null;
    await open(o.id);
    await loadRows();
  }
}
async function upload() {
  if (!file.value) return;
  const o = detail.value.order;
  const body = new FormData();
  body.append("file", file.value);
  body.append("version", String(o.version));
  body.append("caption", photoCaption.value);
  const r = await props.perform(`/orders/${o.id}/photos`, "POST", body);
  if (r) {
    file.value = null;
    photoCaption.value = "";
    await open(o.id);
  }
}
async function exportOrder() {
  try {
    await download(
      `/orders/${detail.value.order.id}/export.csv`,
      `petstay-order-${detail.value.order.id}.csv`,
    );
  } catch (e) {
    emit("error", e);
  }
}
function correct(task) {
  const es = detail.value.entries.filter(
    (e) =>
      e.taskId === task.id &&
      ["TASK_DONE", "TASK_CORRECTION"].includes(e.action),
  );
  const replaced = new Set(es.map((e) => e.supersedesId));
  const latest = es.find((e) => !replaced.has(e.id));
  if (latest) act("correct", { taskId: task.id, entryId: latest.id });
}
watch(
  () => props.initialId,
  (id) => {
    if (id) open(id);
  },
);
onMounted(async () => {
  await load();
  if (props.initialId) await open(props.initialId);
});
</script>
<template>
  <section>
    <p v-if="!ready" class="loading">{{ t("正在读取…", "Loading…") }}</p>
    <template v-if="detail"
      ><header class="detail-heading">
        <button class="text-button" @click="backToList">
          <ArrowLeft :size="17" />{{ t("返回订单", "Back to orders") }}</button
        ><button class="secondary" @click="exportOrder">
          <Download :size="16" />CSV
        </button>
      </header>
      <div class="order-banner">
        <div>
          <p class="eyebrow">
            {{ kindName(detail.order.kind) }} / {{ detail.order.reference }}
          </p>
          <h2>
            {{ detail.snapshot.petName }} · {{ detail.snapshot.providerName }}
          </h2>
          <p>
            <CalendarDays :size="16" />{{ detail.order.startsOn }} →
            {{ detail.order.endsOn }} ·
            {{ windowName(detail.order.visitWindow) }}
          </p>
        </div>
        <div>
          <span class="badge" :class="detail.order.status.toLowerCase()">{{
            stateName(detail.order.status)
          }}</span
          ><strong class="order-total">{{
            money(detail.order.totalMinor)
          }}</strong>
        </div>
      </div>
      <div class="action-bar">
        <button
          v-if="isCoordinator && detail.order.status === 'CONFIRMED'"
          class="primary"
          :disabled="pending"
          @click="act('start')"
        >
          {{ t("确认交接并开始照护", "Start care after handover") }}</button
        ><button
          v-if="
            isCoordinator &&
            detail.order.status === 'IN_PROGRESS' &&
            ['LEGACY', 'NOT_APPLICABLE', 'DEPARTED'].includes(
              detail.order.stayStatus,
            )
          "
          class="primary"
          :disabled="pending"
          @click="act('finish')"
        >
          {{
            t("完成照护并提交主人确认", "Submit care for confirmation")
          }}</button
        ><button
          v-if="
            isCoordinator &&
            detail.order.status === 'IN_PROGRESS' &&
            detail.order.stayStatus === 'ARRIVED'
          "
          class="primary"
          :disabled="pending"
          @click="act('check-out', { recipient: '', belongings: '' })"
        >
          {{ t("登记实际接回", "Record physical collection") }}</button
        ><button
          v-if="isOwner && detail.order.status === 'REVIEW'"
          class="primary"
          :disabled="pending"
          @click="act('accept')"
        >
          <CheckCircle2 :size="17" />{{
            t("确认服务与交接完成", "Confirm service and handover")
          }}</button
        ><button
          v-if="isOwner && detail.order.status === 'REVIEW'"
          class="secondary"
          :disabled="pending"
          @click="act('dispute')"
        >
          {{ t("提出争议", "Raise an issue") }}</button
        ><button
          v-if="isCoordinator && detail.order.status === 'DISPUTED'"
          class="primary"
          :disabled="pending"
          @click="act('respond')"
        >
          {{ t("回复并再次提交", "Respond and resubmit") }}</button
        ><button
          v-if="(isOwner || isProvider) && detail.order.status === 'CONFIRMED'"
          class="quiet danger"
          :disabled="pending"
          @click="act('cancel')"
        >
          {{ t("取消未开始订单", "Cancel before care starts") }}</button
        ><button
          v-if="
            isOwner &&
            detail.order.status === 'COMPLETED' &&
            !detail.reviews.length
          "
          class="secondary"
          :disabled="pending"
          @click="act('review', { rating: 5, comment: '' })"
        >
          {{ t("提交评价", "Submit review") }}
        </button>
      </div>
      <div class="order-layout">
        <div>
          <section class="panel">
            <header class="section-heading">
              <h3>{{ t("每日照护任务", "Daily care tasks") }}</h3>
              <span class="muted"
                >{{ detail.tasks.filter((x) => x.status === "DONE").length }} /
                {{ detail.tasks.length }}</span
              >
            </header>
            <div class="task-list">
              <article v-for="task in detail.tasks" :key="task.id">
                <div>
                  <strong>{{ taskName(task.kind) }}</strong>
                  <p class="muted">
                    {{ task.taskDate }} · {{ stateName(task.status)
                    }}<template v-if="detail.order.managedCare">
                      · {{ t("责任人", "Assigned account") }} #{{
                        detail.assignments.find(
                          (a) => a.taskDate === task.taskDate,
                        )?.accountId || "—"
                      }}</template
                    >
                  </p>
                </div>
                <button
                  v-if="
                    canTask(task) &&
                    detail.order.departedAt === null &&
                    detail.order.status === 'IN_PROGRESS' &&
                    task.status === 'PENDING' &&
                    task.taskDate <= today
                  "
                  class="secondary compact"
                  :disabled="pending"
                  @click="act('task', { taskId: task.id })"
                >
                  {{ t("记录完成", "Record completion") }}</button
                ><button
                  v-if="
                    canTask(task) &&
                    detail.order.departedAt === null &&
                    detail.order.status === 'IN_PROGRESS' &&
                    task.status === 'DONE'
                  "
                  class="text-button"
                  :disabled="pending"
                  @click="correct(task)"
                >
                  {{ t("追加修订", "Append correction") }}
                </button>
              </article>
            </div>
          </section>
          <section v-if="detail.order.kind === 'BOARDING'" class="panel">
            <h3>{{ t("入住与接回", "Arrival & collection") }}</h3>
            <p>
              <span class="badge">{{
                stateName(detail.order.stayStatus)
              }}</span>
              ·
              {{
                detail.order.managedCare
                  ? t("员工排班订单", "Staff-managed booking")
                  : t("负责人照护", "Provider-owner care")
              }}
            </p>
            <p v-if="detail.order.arrivedAt" class="muted">
              {{ t("实际入住", "Arrived") }}
              {{ new Date(detail.order.arrivedAt).toLocaleString() }}
            </p>
            <p v-if="detail.order.departedAt" class="muted">
              {{ t("实际接回", "Collected") }}
              {{ new Date(detail.order.departedAt).toLocaleString() }}
            </p>
            <p
              v-if="
                detail.order.arrivedAt &&
                !detail.order.departedAt &&
                today >= detail.order.endsOn
              "
              class="message error"
            >
              {{
                t(
                  "超期未接回，继续占用门店容量。",
                  "Collection is overdue; shop capacity remains occupied.",
                )
              }}
            </p>
            <article
              v-for="h in detail.handovers"
              :key="h.id"
              class="money-record"
            >
              <strong>{{
                h.kind === "CHECK_IN"
                  ? t("入住交接", "Arrival handover")
                  : t("接回交接", "Collection handover")
              }}</strong>
              <p class="pre-wrap">{{ h.conditionNote }}</p>
              <p v-if="h.recipient">
                {{ t("接回人", "Recipient") }}: {{ h.recipient }}
              </p>
              <p class="pre-wrap">
                {{ t("物品交接", "Belongings") }}: {{ h.belongings || "—" }}
              </p>
              <small
                >{{ new Date(h.createdAt).toLocaleString() }} ·
                {{ t("办理账号", "Recorded by account") }} #{{
                  h.actorId
                }}</small
              >
            </article>
          </section>
          <section class="panel">
            <h3>{{ t("照护与交接记录", "Care and handover history") }}</h3>
            <ol class="timeline">
              <li v-for="entry in detail.entries" :key="entry.id">
                <span class="timeline-dot" />
                <div>
                  <header>
                    <strong>{{
                      entry.action === "TASK_DONE"
                        ? t("任务完成", "Task completed")
                        : entry.action === "TASK_CORRECTION"
                          ? t("追加修订", "Correction appended")
                          : stateName(entry.action)
                    }}</strong
                    ><time>{{
                      new Date(entry.createdAt).toLocaleString()
                    }}</time>
                  </header>
                  <p class="pre-wrap">{{ entry.note }}</p>
                  <small v-if="entry.supersedesId" class="muted"
                    >{{ t("修订记录", "Corrects entry") }} #{{
                      entry.supersedesId
                    }}
                    · {{ t("原记录继续保留", "Original retained") }}</small
                  >
                </div>
              </li>
            </ol>
          </section>
          <section class="panel">
            <header class="section-heading">
              <h3>{{ t("私有照护照片", "Private care photos") }}</h3>
              <span class="muted"
                >{{ detail.photos.length }} {{ t("张", "photos") }}</span
              >
            </header>
            <div class="photo-grid">
              <figure v-for="photo in detail.photos" :key="photo.id">
                <a :href="photo.url" target="_blank" rel="noopener"
                  ><img :src="photo.url" :alt="photo.caption"
                /></a>
                <figcaption>
                  {{ photo.caption
                  }}<small>{{
                    new Date(photo.createdAt).toLocaleString()
                  }}</small>
                </figcaption>
              </figure>
            </div>
            <p v-if="!detail.photos.length" class="muted">
              {{ t("暂无照护照片。", "No care photos yet.") }}
            </p>
            <p v-if="photoQuota" class="hint">
              {{ t("上海上传日期", "Upload date in Shanghai") }}
              {{ photoQuota.date }} · {{ t("今日", "Today") }}
              {{ photoQuota.dailyCount }}/{{ photoQuota.dailyLimit }} ·
              {{ t("整单存储", "Booking storage") }}
              {{ (photoQuota.totalBytes / 1048576).toFixed(2) }}/100 MiB
            </p>
            <form
              v-if="
                detail.canUpload &&
                detail.order.departedAt === null &&
                detail.order.status === 'IN_PROGRESS'
              "
              class="photo-upload"
              @submit.prevent="upload"
            >
              <label
                >{{ t("照片说明", "Photo caption")
                }}<input
                  v-model="photoCaption"
                  maxlength="300"
                  required /></label
              ><label
                >{{ t("PNG / JPEG，最多2MiB", "PNG / JPEG, at most 2 MiB")
                }}<input
                  type="file"
                  accept="image/png,image/jpeg"
                  required
                  @change="file = $event.target.files[0]" /></label
              ><button
                class="secondary"
                :disabled="
                  pending ||
                  !file ||
                  (photoQuota && photoQuota.dailyCount >= photoQuota.dailyLimit)
                "
              >
                <Camera :size="16" />{{
                  t("上传照护照片", "Upload care photo")
                }}
              </button>
            </form>
          </section>
        </div>
        <aside>
          <section class="panel">
            <h3>{{ t("双方交接资料", "Private handover details") }}</h3>
            <dl class="detail-list">
              <dt>{{ t("宠物", "Pet") }}</dt>
              <dd>
                {{ detail.snapshot.petName }} ·
                {{ speciesName(detail.snapshot.species) }} ·
                {{ detail.snapshot.weightKg }}kg
              </dd>
              <dt>{{ t("服务地点", "Service address") }}</dt>
              <dd class="pre-wrap">{{ detail.snapshot.serviceAddress }}</dd>
              <dt>{{ t("宠物主", "Owner") }}</dt>
              <dd>
                {{ detail.snapshot.ownerName }}<br />{{
                  detail.snapshot.ownerContact
                }}
              </dd>
              <dt>{{ t("照护方", "Provider") }}</dt>
              <dd>
                {{ detail.snapshot.providerName }}<br />{{
                  detail.snapshot.providerContact
                }}
              </dd>
              <dt>{{ t("宠物照护要求", "Pet care instructions") }}</dt>
              <dd class="pre-wrap">
                {{ detail.snapshot.petCareNotes || "—" }}
              </dd>
              <dt>{{ t("本次补充说明", "Request instructions") }}</dt>
              <dd class="pre-wrap">
                {{ detail.snapshot.requestCareNotes || "—" }}
              </dd>
            </dl>
          </section>
          <section class="panel">
            <h3>
              {{ t("固定报价与线下款项", "Fixed quote & offline records") }}
            </h3>
            <dl class="detail-list">
              <dt>{{ t("原报价", "Agreed quote") }}</dt>
              <dd>
                {{ money(detail.snapshot.unitMinor) }} ×
                {{ detail.snapshot.units }} =
                {{ money(detail.order.totalMinor) }}
              </dd>
              <dt>{{ t("已登记净收款", "Recorded net receipts") }}</dt>
              <dd>{{ money(detail.netPaidMinor) }}</dd>
              <dt>{{ t("报价与净收款差额", "Quote minus net receipts") }}</dt>
              <dd>
                {{ money(detail.order.totalMinor - detail.netPaidMinor) }}
              </dd>
            </dl>
            <p class="hint">
              {{
                t(
                  "登记已核实的线下交易，不发起实际扣款。提前结束不自动重算固定报价。",
                  "Records verified offline transactions, not actual charges. Early completion does not automatically reprice the quote.",
                )
              }}
            </p>
            <button
              v-if="isProvider && detail.order.status !== 'CANCELLED'"
              class="secondary full"
              :disabled="pending"
              @click="
                act('money', {
                  kind:
                    detail.order.status === 'COMPLETED' ? 'REFUND' : 'RECEIPT',
                  amountYuan: '',
                  reference: '',
                  originalId: '',
                })
              "
            >
              {{ t("登记线下收款 / 退款", "Record offline receipt / refund") }}
            </button>
            <article
              v-for="entry in detail.money"
              :key="entry.id"
              class="money-record"
            >
              <header>
                <strong>{{
                  entry.kind === "RECEIPT"
                    ? t("收款登记", "Receipt record")
                    : t("原款退款", "Original refund")
                }}</strong
                ><span>{{ money(entry.amountMinor) }}</span>
              </header>
              <p>{{ entry.reference }}</p>
              <p class="muted">{{ entry.note }}</p>
            </article>
          </section>
          <section v-if="detail.reviews.length" class="panel">
            <h3>{{ t("服务评价", "Service review") }}</h3>
            <article v-for="r in detail.reviews" :key="r.id">
              <strong>{{ r.rating }} / 5 · {{ stateName(r.status) }}</strong>
              <p class="pre-wrap">{{ r.comment }}</p>
              <p v-if="r.moderationNote" class="hint">{{ r.moderationNote }}</p>
            </article>
          </section>
        </aside>
      </div>
    </template>
    <template v-else-if="ready"
      ><div class="list-tools">
        <label class="inline-search"
          ><Search :size="17" /><input
            v-model="q"
            :placeholder="t('搜索订单号', 'Search reference')"
            maxlength="80"
            @keyup.enter="search" /></label
        ><select
          v-model="status"
          :aria-label="t('订单状态', 'Order status')"
          @change="search"
        >
          <option value="">{{ t("全部状态", "All states") }}</option>
          <option
            v-for="s in [
              'CONFIRMED',
              'IN_PROGRESS',
              'REVIEW',
              'DISPUTED',
              'COMPLETED',
              'CANCELLED',
            ]"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option>
        </select>
        <button class="secondary" @click="search">
          {{ t("查询", "Search") }}
        </button>
      </div>
      <div v-if="listError" class="empty-state">
        <p>
          {{ t("订单读取失败，请重试。", "Could not load bookings. Retry.") }}
        </p>
        <button class="secondary" @click="load">
          {{ t("重试", "Retry") }}
        </button>
      </div>
      <div v-else-if="!rows.length" class="empty-state">
        <CalendarDays :size="38" />
        <h3>{{ t("暂无照护订单", "No care bookings yet") }}</h3>
        <p>
          {{
            t(
              "双方确认报价后，订单会显示在这里。",
              "Bookings appear here after offer confirmation.",
            )
          }}
        </p>
      </div>
      <div class="request-list">
        <article v-for="row in rows" :key="row.id" class="panel request-card">
          <header>
            <div>
              <p class="eyebrow">{{ kindName(row.kind) }}</p>
              <h3>{{ row.reference }}</h3>
            </div>
            <span class="badge" :class="row.status.toLowerCase()">{{
              stateName(row.status)
            }}</span>
          </header>
          <p class="date-line">
            <CalendarDays :size="17" />{{ row.startsOn }} → {{ row.endsOn }} ·
            {{ windowName(row.visitWindow) }}
          </p>
          <footer>
            <strong>{{ money(row.totalMinor) }}</strong
            ><button class="text-button" @click="open(row.id)">
              {{ t("照护记录与交接", "Care and handover")
              }}<ArrowRight :size="17" />
            </button>
          </footer>
        </article>
      </div>
      <div v-if="total > 12" class="pagination">
        <button :disabled="page === 1" @click="turnPage(page - 1)">
          {{ t("上一页", "Previous") }}</button
        ><span>{{ page }} / {{ pages }}</span
        ><button :disabled="page >= pages" @click="turnPage(page + 1)">
          {{ t("下一页", "Next") }}
        </button>
      </div></template
    >
    <div v-if="form && detail" class="modal-backdrop">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('订单操作', 'Order action')"
      >
        <header>
          <h2>
            {{
              form.action === "money"
                ? t("线下交易登记", "Offline transaction record")
                : form.action === "review"
                  ? t("提交服务评价", "Submit service review")
                  : form.action === "task"
                    ? t("记录实际照护完成", "Record actual care completion")
                    : form.action === "correct"
                      ? t("追加照护记录修订", "Append care correction")
                      : t("确认订单操作", "Confirm booking action")
            }}
          </h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="form = null"
          >
            <X :size="20" />
          </button>
        </header>
        <p class="muted">
          {{ detail.order.reference }} · {{ stateName(detail.order.status) }}
        </p>
        <form @submit.prevent="submit">
          <template
            v-if="
              form.action === 'check-out' ||
              (form.action === 'start' &&
                detail.order.stayStatus === 'EXPECTED')
            "
            ><label v-if="form.action === 'check-out'"
              >{{ t("实际接回人", "Actual recipient")
              }}<input
                v-model="form.recipient"
                maxlength="120"
                required /></label
            ><label
              >{{ t("随行物品与交还说明", "Belongings & return note")
              }}<textarea
                v-model="form.belongings"
                maxlength="1800"
                rows="3"
              /></label
          ></template>
          <template v-if="form.action === 'money'"
            ><label
              >{{ t("类型", "Type")
              }}<select v-model="form.kind">
                <option
                  v-if="detail.order.status !== 'COMPLETED'"
                  value="RECEIPT"
                >
                  {{ t("已核实线下收款", "Verified offline receipt") }}
                </option>
                <option value="REFUND">
                  {{ t("退回原收款", "Refund original receipt") }}
                </option>
              </select></label
            ><label v-if="form.kind === 'REFUND'"
              >{{ t("原收款", "Original receipt")
              }}<select v-model="form.originalId" required>
                <option value="">{{ t("请选择", "Choose") }}</option>
                <option
                  v-for="e in detail.money.filter((x) => x.kind === 'RECEIPT')"
                  :key="e.id"
                  :value="e.id"
                >
                  {{ e.reference }} · {{ money(e.amountMinor) }}
                </option>
              </select></label
            ><label
              >{{ t("金额（元）", "Amount (CNY)")
              }}<input
                v-model="form.amountYuan"
                type="number"
                min="0.01"
                max="300000"
                step="0.01"
                required /></label
            ><label
              >{{ t("已核实的流水参考号", "Verified transaction reference")
              }}<input
                v-model="form.reference"
                required
                maxlength="120" /></label></template
          ><template v-else-if="form.action === 'review'"
            ><label
              >{{ t("评分", "Rating")
              }}<select v-model="form.rating">
                <option v-for="n in [5, 4, 3, 2, 1]" :key="n" :value="n">
                  {{ n }} / 5
                </option>
              </select></label
            ><label
              >{{
                t(
                  "评价（审核后公开，请勿含私密资料）",
                  "Review (published after approval; no private details)",
                )
              }}<textarea
                v-model="form.comment"
                required
                maxlength="1200"
                rows="4"
              /></label></template
          ><label v-if="form.action !== 'review'"
            >{{ t("实际情况与交接说明", "Actual care or handover note")
            }}<textarea
              v-model="form.note"
              required
              maxlength="1000"
              rows="4"
            />
          </label>
          <p v-if="form.action === 'finish'" class="hint">
            {{
              t(
                "截至今天的任务必须完成；提前结束会取消未来任务但保留历史，固定金额不自动改变。",
                "Complete all tasks due through today. Early departure cancels future tasks with history retained; the fixed total stays unchanged.",
              )
            }}
          </p>
          <footer class="dialog-footer">
            <button class="secondary" type="button" @click="form = null">
              {{ t("返回", "Back") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("确认提交", "Confirm submission") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
