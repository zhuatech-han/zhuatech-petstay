<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Search,
  MapPin,
  PawPrint,
  Home,
  DoorOpen,
  ArrowRight,
  X,
  Star,
} from "@lucide/vue";
import { api } from "../api.js";
import { t, money, kindName } from "../ui.js";
defineProps({ pending: Boolean });
const emit = defineEmits(["error", "request"]);
const rows = ref([]),
  loading = ref(false),
  q = ref(""),
  page = ref(1),
  detail = ref(null),
  reviews = ref([]);
const filters = ref({
  city: "",
  district: "",
  kind: "",
  species: "",
  startsOn: "",
  endsOn: "",
  visitWindow: "AM",
});
const filtered = computed(() =>
  rows.value.filter((r) =>
    (r.displayName + " " + r.intro)
      .toLowerCase()
      .includes(q.value.toLowerCase()),
  ),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 9, page.value * 9),
);
/** 搜索真实审核目录和日期容量，不放虚构服务方或虚构评价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function search() {
  loading.value = true;
  try {
    const params = new URLSearchParams();
    for (const [k, v] of Object.entries(filters.value)) if (v) params.set(k, v);
    rows.value = await api("/public/providers?" + params);
    page.value = 1;
  } catch (e) {
    emit("error", e);
  } finally {
    loading.value = false;
  }
}
async function open(row) {
  detail.value = row;
  reviews.value = [];
  try {
    reviews.value = await api("/public/providers/" + row.id + "/reviews");
  } catch (e) {
    emit("error", e);
  }
}
onMounted(search);
</script>
<template>
  <section class="market-section">
    <form class="search-panel" @submit.prevent="search">
      <div class="search-top">
        <label
          >{{ t("城市", "City")
          }}<input
            v-model="filters.city"
            :placeholder="t('输入城市', 'Enter city')"
            maxlength="80" /></label
        ><label
          >{{ t("地区", "District")
          }}<input
            v-model="filters.district"
            :placeholder="t('输入地区', 'Enter district')"
            maxlength="80" /></label
        ><label
          >{{ t("服务类型", "Care type")
          }}<select
            v-model="filters.kind"
            :required="!!filters.startsOn || !!filters.endsOn"
          >
            <option value="">{{ t("全部服务", "All services") }}</option>
            <option value="BOARDING">{{ t("宠物寄养", "Boarding") }}</option>
            <option value="HOME_VISIT">
              {{ t("上门喂养与照护", "Home visits") }}
            </option>
          </select></label
        ><label
          >{{ t("宠物", "Pet")
          }}<select v-model="filters.species">
            <option value="">{{ t("猫与犬", "Cats & dogs") }}</option>
            <option value="CAT">{{ t("猫", "Cat") }}</option>
            <option value="DOG">{{ t("犬", "Dog") }}</option>
          </select></label
        ><button class="primary" :disabled="loading">
          <Search :size="18" />{{
            loading ? t("查询中", "Searching") : t("查找照护", "Find care")
          }}
        </button>
      </div>
      <details>
        <summary>{{ t("按服务日期筛选", "Filter by service dates") }}</summary>
        <div class="form-grid">
          <label
            >{{ t("开始日期", "Start date")
            }}<input
              v-model="filters.startsOn"
              type="date"
              :required="!!filters.endsOn" /></label
          ><label
            >{{ t("结束日期（不含）", "End date (exclusive)")
            }}<input
              v-model="filters.endsOn"
              type="date"
              :required="!!filters.startsOn" /></label
          ><label v-if="filters.kind === 'HOME_VISIT'"
            >{{ t("每日上门时段", "Daily visit slot")
            }}<select v-model="filters.visitWindow">
              <option value="AM">{{ t("上午 09–12", "Morning 09–12") }}</option>
              <option value="PM">
                {{ t("下午 14–17", "Afternoon 14–17") }}
              </option>
              <option value="EVENING">
                {{ t("晚间 18–21", "Evening 18–21") }}
              </option>
            </select></label
          >
        </div>
      </details>
    </form>
    <header class="section-heading">
      <div>
        <p class="eyebrow">{{ t("照护服务目录", "Care directory") }}</p>
        <h2>{{ t("寻找适合的照护方", "Explore care providers") }}</h2>
      </div>
      <span class="muted"
        >{{ filtered.length }} {{ t("位符合条件", "matching providers") }}</span
      >
    </header>
    <div class="list-tools">
      <label class="inline-search"
        ><Search :size="17" /><input
          v-model="q"
          :placeholder="t('按昵称或服务说明筛选', 'Filter name or description')"
          @input="page = 1"
      /></label>
    </div>
    <div v-if="!loading && !filtered.length" class="empty-state">
      <PawPrint :size="40" />
      <h3>
        {{ t("暂无符合条件的已审核照护方", "No approved providers match yet") }}
      </h3>
      <p>
        {{
          t(
            "调整地区或日期，或发布需求等待照护者申请。",
            "Try another area or date, or post a request for providers to apply.",
          )
        }}
      </p>
      <button class="secondary" @click="emit('request')">
        {{ t("发布照护需求", "Post a care request") }}
      </button>
    </div>
    <div class="provider-grid">
      <article v-for="row in visible" :key="row.id" class="provider-card">
        <header>
          <div class="pet-avatar"><PawPrint :size="28" /></div>
          <div>
            <h3>{{ row.displayName }}</h3>
            <span class="small-location"
              ><MapPin :size="14" />{{ row.city }} · {{ row.district }}</span
            >
          </div>
          <span
            class="badge approved"
            :title="
              t(
                '服务资料审核，不代表实名或资质认证',
                'Service-profile review, not identity or certification',
              )
            "
            >{{ t("资料已审核", "Reviewed") }}</span
          >
        </header>
        <div class="card-tags">
          <span v-if="row.boarding"
            ><Home :size="14" />{{ kindName("BOARDING") }}</span
          ><span v-if="row.homeVisit"
            ><DoorOpen :size="14" />{{ kindName("HOME_VISIT") }}</span
          ><span
            >{{
              row.species === "BOTH"
                ? t("猫 / 犬", "Cat / Dog")
                : row.species === "CAT"
                  ? t("猫", "Cat")
                  : t("犬", "Dog")
            }}
            · ≤{{ row.maxWeightKg }}kg</span
          >
        </div>
        <p class="provider-intro">{{ row.intro }}</p>
        <div class="prices">
          <span v-if="row.boarding"
            ><strong>{{ money(row.boardingPriceMinor) }}</strong> /
            {{ t("晚", "night") }}</span
          ><span v-if="row.homeVisit"
            ><strong>{{ money(row.visitPriceMinor) }}</strong> /
            {{ t("次", "visit") }}</span
          >
        </div>
        <footer>
          <span v-if="row.reviewCount" class="rating"
            ><Star :size="14" />{{ row.rating.toFixed(1) }} ·
            {{ row.reviewCount }}{{ t("条评价", " reviews") }}</span
          ><span v-else class="muted">{{
            t("尚无公开评价", "No published reviews")
          }}</span
          ><button class="text-button" @click="open(row)">
            {{ t("了解服务", "View details") }}<ArrowRight :size="16" />
          </button>
        </footer>
      </article>
    </div>
    <div v-if="filtered.length > 9" class="pagination">
      <button :disabled="page === 1" @click="page--">
        {{ t("上一页", "Previous") }}</button
      ><span>{{ page }} / {{ Math.ceil(filtered.length / 9) }}</span
      ><button :disabled="page * 9 >= filtered.length" @click="page++">
        {{ t("下一页", "Next") }}
      </button>
    </div>
    <div v-if="detail" class="modal-backdrop" @click.self="detail = null">
      <section
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('服务资料', 'Service details')"
      >
        <header>
          <div>
            <p class="eyebrow">{{ detail.city }} · {{ detail.district }}</p>
            <h2>{{ detail.displayName }}</h2>
          </div>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="detail = null"
          >
            <X :size="20" />
          </button>
        </header>
        <p class="pre-wrap">{{ detail.intro }}</p>
        <div class="prices">
          <span v-if="detail.boarding"
            >{{ kindName("BOARDING") }} {{ money(detail.boardingPriceMinor) }} /
            {{ t("晚", "night") }}</span
          ><span v-if="detail.homeVisit"
            >{{ kindName("HOME_VISIT") }} {{ money(detail.visitPriceMinor) }} /
            {{ t("次", "visit") }}</span
          >
        </div>
        <p class="hint">
          {{
            t(
              "详细地址与联系方式在双方确认订单后提供。",
              "Exact address and contact details are shared after booking confirmation.",
            )
          }}
        </p>
        <h3>{{ t("已公开评价", "Published reviews") }}</h3>
        <p v-if="!reviews.length" class="muted">
          {{ t("暂无公开评价。", "No published reviews.") }}
        </p>
        <article v-for="(r, i) in reviews" :key="i" class="review-card">
          <strong>{{ r.rating }} / 5 · {{ r.author }}</strong>
          <p class="pre-wrap">{{ r.comment }}</p>
        </article>
        <button
          class="primary full"
          @click="
            detail = null;
            emit('request');
          "
        >
          {{ t("发布需求并匹配", "Post a request and match") }}
        </button>
      </section>
    </div>
  </section>
</template>
