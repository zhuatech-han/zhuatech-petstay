<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { api } from "../api.js";
import { t, kindName, windowName, stateName, taskName } from "../ui.js";
const props = defineProps({
  me: { type: Object, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "open-order"]);
const shops = ref([]),
  selected = ref(""),
  calendar = ref(null),
  loading = ref(false),
  form = ref(null);
const from = ref(
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date()),
);
const to = ref("");
let seq = 0;
function endDate(date, days = 7) {
  const d = new Date(date + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
to.value = endDate(from.value);
const accepted = computed(() =>
  shops.value.filter((s) => s.status === "ACTIVE"),
);
const days = computed(() => {
  const out = [];
  if (!calendar.value) return out;
  for (let d = from.value; d < to.value && out.length < 31; d = endDate(d, 1))
    out.push(d);
  return out;
});
const activeStaff = computed(
  () => calendar.value?.staff.filter((s) => s.status === "ACTIVE") || [],
);
function staffName(id) {
  return (
    calendar.value?.staff.find((s) => s.accountId === id)?.displayName ||
    (id === props.me.id ? props.me.displayName : t("员工 #", "Staff #") + id)
  );
}
function assignment(order, date) {
  return calendar.value.assignments.find(
    (a) => a.orderId === order && a.taskDate === date,
  );
}
function dayTasks(date) {
  return calendar.value.tasks.filter((x) => x.taskDate === date);
}
/** 只接受最后一次已选择门店的读取结果，避免切换门店后覆盖旧资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  const n = ++seq;
  loading.value = true;
  calendar.value = null;
  try {
    const all = await api("/shops");
    if (n !== seq) return;
    shops.value = all;
    if (
      !selected.value ||
      !all.some((s) => s.id === Number(selected.value) && s.status === "ACTIVE")
    )
      selected.value = all.find((s) => s.status === "ACTIVE")?.id || "";
    if (selected.value) {
      const result = await api(
        `/shops/${selected.value}/calendar?` +
          new URLSearchParams({ from: from.value, to: to.value }),
      );
      if (n === seq) calendar.value = result;
    }
  } catch (e) {
    if (n === seq) emit("error", e);
  } finally {
    if (n === seq) loading.value = false;
  }
}
async function staffAction(s, action) {
  const r = await props.perform(
    `/staff/${s.membershipId || s.id}/${action}`,
    "POST",
    { version: s.version },
  );
  if (r) await load();
}
function openRoster() {
  form.value = {
    type: "roster",
    accountId: calendar.value.canManage ? "" : props.me.id,
    startsOn: from.value,
    endsOn: to.value,
    visitWindow: "DAY",
  };
}
function assign(o, date) {
  form.value = {
    type: "assign",
    orderId: o.id,
    version: o.version,
    taskDate: date,
    accountId: assignment(o.id, date)?.accountId || "",
    note: "",
    remove: false,
  };
}
async function save() {
  const f = form.value;
  let path, body;
  if (f.type === "invite") {
    path = `/shops/${selected.value}/invite`;
    body = { username: f.username, role: f.role };
    if (f.version) body.version = f.version;
  } else if (f.type === "roster") {
    path = `/shops/${selected.value}/rosters`;
    body = {
      accountId: f.accountId,
      startsOn: f.startsOn,
      endsOn: f.endsOn,
      visitWindow: f.visitWindow,
    };
  } else {
    path = `/orders/${f.orderId}/assign`;
    body = {
      version: f.version,
      taskDate: f.taskDate,
      accountId: f.accountId,
      note: f.note,
      remove: f.remove,
    };
  }
  const r = await props.perform(path, "POST", body);
  if (r) {
    form.value = null;
    await load();
  }
}
async function policy() {
  const p = calendar.value.policy;
  const r = await props.perform(`/shops/${selected.value}/policy`, "POST", {
    version: p.version,
    workforceEnabled: !p.workforceEnabled,
  });
  if (r) await load();
}
async function removeRoster(r) {
  const result = await props.perform(`/rosters/${r.id}`, "DELETE", {
    version: r.version,
  });
  if (result) await load();
}
onMounted(load);
</script>
<template>
  <section>
    <div v-if="shops.some((s) => s.status === 'INVITED')" class="panel">
      <h2>{{ t("待接受的门店邀请", "Pending shop invitations") }}</h2>
      <article
        v-for="s in shops.filter((s) => s.status === 'INVITED')"
        :key="s.membershipId"
        class="list-row"
      >
        <div>
          <strong>{{ s.name }}</strong>
          <p>
            {{
              s.role === "MANAGER"
                ? t("店长", "Manager")
                : t("照护员工", "Carer")
            }}
          </p>
        </div>
        <div class="row-actions">
          <button
            class="primary compact"
            :disabled="pending"
            @click="staffAction(s, 'accept')"
          >
            {{ t("接受邀请", "Accept invitation") }}</button
          ><button
            class="secondary compact"
            :disabled="pending"
            @click="staffAction(s, 'reject')"
          >
            {{ t("拒绝", "Decline") }}
          </button>
        </div>
      </article>
    </div>
    <form class="filter-bar" @submit.prevent="load">
      <label
        >{{ t("门店 / 团队", "Shop / team")
        }}<select v-model="selected" @change="load">
          <option value="">{{ t("请选择", "Choose") }}</option>
          <option v-for="s in accepted" :key="s.id" :value="s.id">
            {{ s.name }}
          </option>
        </select></label
      ><label
        >{{ t("开始日期", "From")
        }}<input v-model="from" type="date" required /></label
      ><label
        >{{ t("结束日期（不含）", "Until (exclusive)")
        }}<input v-model="to" type="date" required /></label
      ><button class="secondary" :disabled="loading">
        {{ t("查看日程", "View schedule") }}
      </button>
    </form>
    <p v-if="loading" class="loading">{{ t("正在读取…", "Loading…") }}</p>
    <p v-else-if="!calendar" class="empty">
      {{
        t(
          "提交照护服务资料或接受门店邀请后，可在这里安排照护。",
          "Submit a provider profile or accept a shop invitation to use scheduling.",
        )
      }}
    </p>
    <template v-if="calendar">
      <section class="panel">
        <header class="section-heading">
          <div>
            <h2>{{ calendar.name }}</h2>
            <p class="muted">
              {{
                calendar.policy.workforceEnabled
                  ? t(
                      "新订单使用员工排班",
                      "New bookings require staff assignments",
                    )
                  : t(
                      "新订单由负责人照护",
                      "New bookings are cared for by the owner",
                    )
              }}
            </p>
          </div>
          <button
            v-if="me.id === calendar.ownerAccountId"
            class="secondary"
            :disabled="pending"
            @click="policy"
          >
            {{
              calendar.policy.workforceEnabled
                ? t("停用新订单排班", "Disable for new bookings")
                : t("启用新订单排班", "Enable for new bookings")
            }}
          </button>
        </header>
        <p class="hint">
          {{
            t(
              "上门每人每时段最多一单；寄养每人每天最多五只，不能与上门混排。开始前须安排全部任务日。",
              "One home visit per person per window; up to five boarding pets per day, with no mixed home visits. Assign every task day before starting.",
            )
          }}
        </p>
      </section>
      <section class="panel">
        <header class="section-heading">
          <h3>{{ t("照护日程", "Care schedule") }}</h3>
          <span class="muted">{{ from }} → {{ to }}</span>
        </header>
        <div class="schedule-days">
          <article v-for="day in days" :key="day" class="schedule-day">
            <h4>{{ day }}</h4>
            <p v-if="!dayTasks(day).length" class="muted">
              {{ t("暂无任务", "No tasks") }}
            </p>
            <article
              v-for="o in calendar.orders.filter((o) =>
                dayTasks(day).some((t) => t.orderId === o.id),
              )"
              :key="o.id"
              class="schedule-booking"
            >
              <button class="text-button" @click="emit('open-order', o.id)">
                {{ o.reference }}
              </button>
              <p>
                {{ kindName(o.kind) }} · {{ windowName(o.visitWindow) }}
                <span v-if="o.overdue" class="badge disputed">{{
                  t("超期未接回", "Collection overdue")
                }}</span>
              </p>
              <p>
                {{ stateName(o.status) }} ·
                {{
                  o.managedCare
                    ? assignment(o.id, day)
                      ? staffName(assignment(o.id, day).accountId)
                      : t("未安排人员", "Unassigned")
                    : t("门店负责人", "Shop owner")
                }}
              </p>
              <p class="muted">
                {{
                  dayTasks(day)
                    .filter((x) => x.orderId === o.id)
                    .map((x) => taskName(x.kind) + " · " + stateName(x.status))
                    .join(" / ")
                }}
              </p>
              <button
                v-if="
                  calendar.canManage &&
                  o.managedCare &&
                  ['CONFIRMED', 'IN_PROGRESS'].includes(o.status) &&
                  dayTasks(day)
                    .filter((x) => x.orderId === o.id)
                    .every((x) => x.status === 'PENDING')
                "
                class="secondary compact"
                :disabled="pending"
                @click="assign(o, day)"
              >
                {{ t("安排 / 调整人员", "Assign / change staff") }}
              </button>
            </article>
          </article>
        </div>
      </section>
      <section v-if="calendar.orders.some((o) => o.overdue)" class="panel">
        <h3>{{ t("超期在养", "Overdue boarding") }}</h3>
        <p v-for="o in calendar.orders.filter((o) => o.overdue)" :key="o.id">
          <button class="text-button" @click="emit('open-order', o.id)">
            {{ o.reference }}
          </button>
          · {{ t("预约接回", "Planned collection") }} {{ o.endsOn }}
        </p>
      </section>
      <section v-if="calendar.canManage" class="panel">
        <header class="section-heading">
          <h3>{{ t("门店员工", "Shop staff") }}</h3>
          <button
            class="secondary"
            @click="form = { type: 'invite', username: '', role: 'CARER' }"
          >
            {{ t("邀请员工", "Invite staff") }}
          </button>
        </header>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("员工", "Staff") }}</th>
                <th>{{ t("岗位", "Role") }}</th>
                <th>{{ t("状态", "Status") }}</th>
                <th>{{ t("操作", "Action") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in calendar.staff" :key="s.id">
                <td>
                  {{ s.displayName }} <small>#{{ s.accountId }}</small>
                </td>
                <td>
                  {{
                    s.role === "OWNER"
                      ? t("负责人", "Owner")
                      : s.role === "MANAGER"
                        ? t("店长", "Manager")
                        : t("照护员工", "Carer")
                  }}
                </td>
                <td>{{ stateName(s.status) }}</td>
                <td>
                  <button
                    v-if="
                      s.role !== 'OWNER' &&
                      ['ACTIVE', 'INVITED'].includes(s.status) &&
                      (s.role === 'CARER' || me.id === calendar.ownerAccountId)
                    "
                    class="quiet danger"
                    :disabled="pending"
                    @click="staffAction(s, 'revoke')"
                  >
                    {{ t("撤销关系", "Revoke access") }}</button
                  ><button
                    v-else-if="['REVOKED', 'REJECTED'].includes(s.status)"
                    class="quiet"
                    @click="
                      form = {
                        type: 'invite',
                        username: s.username,
                        role: s.role,
                        version: s.version,
                      }
                    "
                  >
                    {{ t("重新邀请", "Invite again") }}</button
                  ><span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section class="panel">
        <header class="section-heading">
          <h3>{{ t("可工作班次", "Working roster") }}</h3>
          <button class="secondary" @click="openRoster">
            {{ t("添加班次", "Add roster") }}
          </button>
        </header>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("员工", "Staff") }}</th>
                <th>{{ t("日期", "Dates") }}</th>
                <th>{{ t("窗口", "Window") }}</th>
                <th>{{ t("操作", "Action") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in calendar.rosters" :key="r.id">
                <td>{{ staffName(r.accountId) }}</td>
                <td>{{ r.startsOn }} → {{ r.endsOn }}</td>
                <td>
                  {{
                    r.visitWindow === "DAY"
                      ? t("整日", "Full day")
                      : windowName(r.visitWindow)
                  }}
                </td>
                <td>
                  <button
                    class="quiet danger"
                    :disabled="pending"
                    @click="removeRoster(r)"
                  >
                    {{ t("删除未占用班次", "Delete unused roster") }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!calendar.rosters.length" class="muted">
          {{ t("当前日期范围没有班次。", "No rosters in this date range.") }}
        </p>
      </section>
    </template>
    <div v-if="form" class="modal-backdrop" @click.self="form = null">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('门店安排', 'Shop arrangement')"
      >
        <header>
          <h2>
            {{
              form.type === "invite"
                ? t("邀请员工", "Invite staff")
                : form.type === "roster"
                  ? t("添加可工作班次", "Add working roster")
                  : t("安排照护人员", "Assign care staff")
            }}
          </h2>
          <button class="quiet" @click="form = null">
            {{ t("关闭", "Close") }}
          </button>
        </header>
        <form @submit.prevent="save">
          <template v-if="form.type === 'invite'"
            ><label
              >{{ t("已注册的账号名", "Registered username")
              }}<input
                v-model="form.username"
                required
                maxlength="60"
                autocomplete="off" /></label
            ><label
              >{{ t("门店岗位", "Shop role")
              }}<select v-model="form.role">
                <option value="CARER">{{ t("照护员工", "Carer") }}</option>
                <option
                  v-if="me.id === calendar.ownerAccountId"
                  value="MANAGER"
                >
                  {{ t("店长", "Manager") }}
                </option>
              </select></label
            >
            <p class="hint">
              {{
                t(
                  "员工需用自己的账号接受邀请；店长不能登记款项或替主人验收。",
                  "The employee must accept from their own account. Managers cannot record money or confirm as the pet owner.",
                )
              }}
            </p></template
          ><template v-else
            ><label v-if="calendar.canManage"
              >{{ t("照护人员", "Care staff")
              }}<select v-model="form.accountId" :required="!form.remove">
                <option value="">{{ t("请选择", "Choose") }}</option>
                <option
                  v-for="s in activeStaff"
                  :key="s.accountId"
                  :value="s.accountId"
                >
                  {{ s.displayName }} #{{ s.accountId }}
                </option>
              </select></label
            ><template v-if="form.type === 'roster'"
              ><label
                >{{ t("开始日期", "From")
                }}<input v-model="form.startsOn" type="date" required /></label
              ><label
                >{{ t("结束日期（不含）", "Until (exclusive)")
                }}<input v-model="form.endsOn" type="date" required /></label
              ><label
                >{{ t("班次窗口", "Roster window")
                }}<select v-model="form.visitWindow">
                  <option value="DAY">{{ t("整日", "Full day") }}</option>
                  <option
                    v-for="w in ['AM', 'PM', 'EVENING']"
                    :key="w"
                    :value="w"
                  >
                    {{ windowName(w) }}
                  </option>
                </select></label
              ></template
            ><template v-else
              ><p>{{ form.taskDate }}</p>
              <label class="check-label"
                ><input v-model="form.remove" type="checkbox" />{{
                  t("移除当天分派", "Remove this day’s assignment")
                }}</label
              ><label
                >{{ t("安排说明", "Assignment note")
                }}<textarea
                  v-model="form.note"
                  required
                  maxlength="1000"
                  rows="3"
                ></textarea></label></template
          ></template>
          <footer class="dialog-footer">
            <button class="secondary" type="button" @click="form = null">
              {{ t("返回", "Back") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存安排", "Save arrangement") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
