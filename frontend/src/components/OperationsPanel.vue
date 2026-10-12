<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { Search, X } from "@lucide/vue";
import { api } from "../api.js";
import { speciesName, t, stateName, kindName, money } from "../ui.js";
const props = defineProps({
  section: { type: String, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "open-order"]);
const data = ref(null),
  tab = ref("providers"),
  q = ref(""),
  page = ref(1),
  editing = ref(null),
  ready = ref(false);
const rows = computed(() =>
  props.section === "audit" ? data.value || [] : data.value?.[tab.value] || [],
);
const filtered = computed(() =>
  rows.value.filter((r) =>
    JSON.stringify(r).toLowerCase().includes(q.value.toLowerCase()),
  ),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
async function load() {
  ready.value = false;
  try {
    data.value = await api("/" + props.section);
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
function edit(row) {
  editing.value = { row, kind: tab.value, status: "APPROVED", note: "" };
}
async function save() {
  const e = editing.value;
  const r = await props.perform(`/moderate/${e.kind}/${e.row.id}`, "POST", {
    version: e.row.version,
    status: e.status,
    note: e.note,
  });
  if (r) {
    editing.value = null;
    await load();
  }
}
onMounted(load);
</script>
<template>
  <section>
    <p v-if="!ready" class="loading">
      {{ t("读取运营数据…", "Loading operations…") }}
    </p>
    <template v-else-if="data && section === 'reports'"
      ><div class="metric-grid">
        <article class="metric">
          <span>{{ t("照护订单", "Care bookings") }}</span
          ><strong>{{ data.orders }}</strong>
        </article>
        <article class="metric">
          <span>{{ t("已审核照护方", "Approved providers") }}</span
          ><strong>{{ data.approvedProviders }}</strong>
        </article>
        <article class="metric">
          <span>{{ t("开放需求", "Open requests") }}</span
          ><strong>{{ data.openRequests }}</strong>
        </article>
        <article class="metric">
          <span>{{ t("登记收款 / 退款", "Recorded receipts / refunds") }}</span
          ><strong class="small-amount"
            >{{ money(data.recordedReceiptsMinor) }} /
            {{ money(data.recordedRefundsMinor) }}</strong
          >
        </article>
      </div>
      <div class="panel">
        <h2>{{ t("订单状态分布", "Booking states") }}</h2>
        <div
          v-for="(count, state) in data.states"
          :key="state"
          class="report-row"
        >
          <span>{{ stateName(state) }}</span>
          <div>
            <i
              :style="{ width: (count / Math.max(data.orders, 1)) * 100 + '%' }"
            />
          </div>
          <strong>{{ count }}</strong>
        </div>
        <p class="hint">
          {{
            t(
              "金额为人工核实的线下登记，不是平台实际代收收入。",
              "Amounts are verified offline records, not platform-collected revenue.",
            )
          }}
        </p>
      </div></template
    >
    <template v-else-if="ready && data"
      ><div v-if="section === 'moderate'" class="tabs">
        <button
          v-for="kind in ['providers', 'reviews', 'orders']"
          :key="kind"
          :class="{ active: tab === kind }"
          @click="
            tab = kind;
            page = 1;
            q = '';
          "
        >
          {{
            kind === "providers"
              ? t("照护资料审核", "Provider profiles")
              : kind === "reviews"
                ? t("评价审核", "Review moderation")
                : t("运营订单", "Bookings")
          }}
        </button>
      </div>
      <div class="list-tools">
        <label class="inline-search"
          ><Search :size="17" /><input
            v-model="q"
            :placeholder="t('搜索运营记录', 'Search operations')"
            @input="page = 1" /></label
        ><span class="muted"
          >{{ filtered.length }} {{ t("条记录", "records") }}</span
        >
      </div>
      <div
        v-if="section === 'moderate' && tab === 'providers'"
        class="review-list"
      >
        <article v-for="row in visible" :key="row.id" class="panel">
          <header class="section-heading">
            <div>
              <h3>{{ row.displayName }}</h3>
              <p>
                {{ row.city }} / {{ row.district }} ·
                {{ speciesName(row.species) }} · ≤{{ row.maxWeightKg }}kg
              </p>
            </div>
            <span class="badge">{{ stateName(row.status) }}</span>
          </header>
          <p class="pre-wrap">{{ row.intro }}</p>
          <p>
            {{
              t(
                "寄养容量 / 上门时段名额：",
                "Boarding / visit-window capacity: ",
              )
            }}{{ row.boardingCapacity }} / {{ row.visitCapacity }}
          </p>
          <p>
            {{
              t(
                "寄养地点（运营私有）：",
                "Boarding location (private operations): ",
              )
            }}{{ row.address || "—" }}
          </p>
          <p>
            {{ t("寄养 / 上门单价：", "Boarding / visit price: ")
            }}{{ money(row.boardingPriceMinor) }} /
            {{ money(row.visitPriceMinor) }}
          </p>
          <p v-if="row.reviewNote" class="hint">{{ row.reviewNote }}</p>
          <button class="secondary" :disabled="pending" @click="edit(row)">
            {{ t("审核 / 暂停", "Review / suspend") }}
          </button>
        </article>
      </div>
      <div
        v-else-if="section === 'moderate' && tab === 'reviews'"
        class="review-list"
      >
        <article v-for="row in visible" :key="row.id" class="panel">
          <header class="section-heading">
            <h3>
              {{ t("订单", "Order") }} #{{ row.orderId }} · {{ row.rating }} / 5
            </h3>
            <span class="badge">{{ stateName(row.status) }}</span>
          </header>
          <p class="pre-wrap">{{ row.comment }}</p>
          <p v-if="row.moderationNote" class="hint">{{ row.moderationNote }}</p>
          <button class="secondary" :disabled="pending" @click="edit(row)">
            {{ t("审核评价", "Moderate review") }}
          </button>
        </article>
      </div>
      <div v-else class="panel table-scroll">
        <table>
          <thead>
            <tr>
              <template v-if="section === 'audit'"
                ><th>{{ t("时间", "Time") }}</th>
                <th>{{ t("操作账号", "Actor") }}</th>
                <th>{{ t("动作", "Action") }}</th>
                <th>{{ t("对象", "Object") }}</th></template
              ><template v-else
                ><th>{{ t("订单号", "Reference") }}</th>
                <th>{{ t("服务", "Service") }}</th>
                <th>{{ t("日期", "Dates") }}</th>
                <th>{{ t("状态", "State") }}</th>
                <th>{{ t("金额", "Amount") }}</th>
                <th>{{ t("详情", "Details") }}</th></template
              >
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <template v-if="section === 'audit'"
                ><td>{{ new Date(row.createdAt).toLocaleString() }}</td>
                <td>{{ row.actor }}</td>
                <td>{{ row.action }}</td>
                <td>{{ row.objectId }}</td></template
              ><template v-else
                ><td>{{ row.reference }}</td>
                <td>{{ kindName(row.kind) }}</td>
                <td>{{ row.startsOn }} → {{ row.endsOn }}</td>
                <td>{{ stateName(row.status) }}</td>
                <td>{{ money(row.totalMinor) }}</td>
                <td>
                  <button
                    class="text-button"
                    @click="emit('open-order', row.id)"
                  >
                    {{ t("查看", "View") }}
                  </button>
                </td></template
              >
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!visible.length" class="empty-state">
        {{ t("暂无记录。", "No records.") }}
      </p>
      <div v-if="filtered.length > 15" class="pagination">
        <button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><span>{{ page }} / {{ Math.ceil(filtered.length / 15) }}</span
        ><button :disabled="page * 15 >= filtered.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </div>
    </template>
    <div v-if="editing" class="modal-backdrop">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('运营审核', 'Moderation')"
      >
        <header>
          <h2>{{ t("记录审核决定", "Record review decision") }}</h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="editing = null"
          >
            <X :size="20" />
          </button>
        </header>
        <form @submit.prevent="save">
          <label
            >{{ t("决定", "Decision")
            }}<select v-model="editing.status">
              <option value="APPROVED">
                {{ t("批准公开", "Approve publication") }}
              </option>
              <option value="REJECTED">{{ t("未通过", "Reject") }}</option>
              <option v-if="editing.kind === 'providers'" value="SUSPENDED">
                {{ t("暂停提供新服务", "Suspend new services") }}
              </option>
            </select></label
          ><label
            >{{ t("审核说明", "Review note")
            }}<textarea
              v-model="editing.note"
              required
              maxlength="1200"
              rows="4"
            />
          </label>
          <p class="hint">
            {{
              t(
                "审核服务资料，不作实名、保险或专业资质认证承诺。",
                "Review service information without claiming identity, insurance or professional certification.",
              )
            }}
          </p>
          <footer class="dialog-footer">
            <button class="secondary" type="button" @click="editing = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存决定", "Save decision") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
