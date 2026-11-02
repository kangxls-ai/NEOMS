<script setup>
import { computed, ref, watch } from "vue";
import { statusNames, typeNames } from "./schema";
import { handlers } from "./workflow";
const props = defineProps({
  kind: String,
  page: String,
  data: Object,
  user: Object,
});
const emit = defineEmits(["view", "preview", "toggle"]);
const query = ref(""),
  station = ref(""),
  status = ref(""),
  type = ref(""),
  person = ref(""),
  year = ref(""),
  dates = ref([]),
  view = ref("all"),
  pageNo = ref(1),
  size = ref(10),
  audits = ref([]);
const approval = computed(() =>
  ["defectApproval", "workApproval", "operationApproval"].includes(props.page),
);
const names = (id) =>
  Object.values(props.data)
    .filter(Array.isArray)
    .flat()
    .find((x) => x.id === id)?.name ||
  id ||
  "—";
const st = (s) => statusNames[s] || s || "—";
const source = computed(() =>
  (props.data[props.kind] || []).filter((x) => x.status !== "removed"),
);
const classify = (x) =>
  x.type ||
  x.subtype ||
  x.sourceType ||
  x.workType ||
  x.inspectionType ||
  x.dutyType ||
  x.firstType ||
  "";
const pending = (x) =>
  props.kind === "defects"
    ? x.status === "pendingApproval"
    : ["pendingSign", "pendingConfirm", "pendingPermit"].includes(x.status);
const tabs = computed(() =>
  approval.value
    ? [
        ["mine", "待我处理"],
        ["pending", "全部待办"],
        ["processed", "我的已处理"],
        ["returned", "已退回"],
      ]
    : props.kind === "orders"
      ? [
          ["all", "全部工单"],
          ["corrective", "消缺"],
          ["maintenance", "定检"],
          ["inspection", "巡检"],
          ["unassigned", "待分配"],
          ["paused", "已挂起"],
          ["finished", "已完成"],
        ]
      : [],
);
async function loadAudits() {
  if (!approval.value) return;
  const r = await fetch("/api/approvals/history?kind=" + props.kind, {
    credentials: "same-origin",
  });
  if (r.ok) audits.value = await r.json();
}
watch(
  () => [props.page, props.data],
  () => {
    view.value = approval.value
      ? "mine"
      : props.page === "unassigned"
        ? "unassigned"
        : props.page === "paused"
          ? "paused"
          : props.page === "acceptance"
            ? "review"
            : "all";
    pageNo.value = 1;
    loadAudits();
  },
  { immediate: true },
);
const filtered = computed(() => {
  let list =
    view.value === "processed"
      ? audits.value.filter((x) => x.auditActor === props.user.id)
      : source.value;
  if (approval.value && view.value === "mine")
    list = list.filter(
      (x) => pending(x) && handlers(x, props.data).ids.includes(props.user.id),
    );
  if (approval.value && view.value === "pending") list = list.filter(pending);
  if (view.value === "returned")
    list = list.filter((x) => x.status === "returned");
  if (["corrective", "maintenance", "inspection"].includes(view.value))
    list = list.filter((x) => x.type === view.value);
  if (["unassigned", "paused"].includes(view.value))
    list = list.filter((x) => x.status === view.value);
  if (view.value === "finished")
    list = list.filter((x) => ["closed", "completed"].includes(x.status));
  if (props.page === "acceptance")
    list = list.filter(
      (x) => x.type === "corrective" && ["review", "closed"].includes(x.status),
    );
  return list
    .filter(
      (x) =>
        (!station.value || x.stationId === station.value) &&
        (!status.value || x.status === status.value) &&
        (!type.value || classify(x) === type.value) &&
        (!person.value ||
          [
            x.assigneeId,
            x.leaderId,
            x.personId,
            x.recipientId,
            x.issuerId,
            x.auditActor,
          ].includes(person.value)) &&
        (!year.value || String(x.year) === year.value) &&
        (!dates.value?.length ||
          ((x.startAt || x.createdAt || "").slice(0, 10) >= dates.value[0] &&
            (x.startAt || x.createdAt || "").slice(0, 10) <= dates.value[1])) &&
        (!query.value ||
          JSON.stringify([
            x.name,
            x.code,
            x.description,
            x.analysis,
            x.measures,
            x.risk,
            x.deviceName,
            x.deviceCode,
            names(x.deviceId),
            names(x.assigneeId),
            names(x.personId),
          ])
            .toLowerCase()
            .includes(query.value.toLowerCase())),
    )
    .toSorted((a, b) =>
      (b.auditTime || b.startAt || b.createdAt || "").localeCompare(
        a.auditTime || a.startAt || a.createdAt || "",
      ),
    );
});
watch(
  [query, station, status, type, person, year, dates, view, size],
  () => (pageNo.value = 1),
);
const paged = computed(() =>
  filtered.value.slice(
    (pageNo.value - 1) * size.value,
    pageNo.value * size.value,
  ),
);
const columns = computed(() =>
  (
    ({
      stations: [
        ["name", "场站名称"],
        ["type", "类型"],
        ["capacity", "装机容量 MW"],
        ["province", "省份"],
      ],
      devices: [
        ["deviceCode", "设备编码"],
        ["name", "设备名称"],
        ["stationId", "场站"],
        ["type", "设备类型"],
        ["temperature", "温度 ℃"],
        ["power", "功率 kW"],
        ["sampledAt", "最新采样"],
      ],
      people: [
        ["name", "姓名"],
        ["username", "账号"],
        ["role", "业务职责"],
        ["stationId", "场站"],
      ],
      teams: [
        ["name", "班组"],
        ["stationId", "场站"],
        ["leaderId", "班组负责人"],
        ["skill", "技能"],
      ],
      shifts: [
        ["stationId", "场站"],
        ["startAt", "开始时间"],
        ["endAt", "结束时间"],
        ["name", "班次"],
        ["assigneeId", "负责人"],
        ["chiefId", "值长"],
        ["handoverState", "班后交代"],
      ],
      maintenanceRules: [
        ["name", "定检工作"],
        ["stationId", "场站"],
        ["workType", "工作类型"],
        ["status", "状态"],
        ["description", "工作内容"],
        ["deviceCount", "设备数"],
        ["times", "触发时点"],
      ],
      inspectionRules: [
        ["name", "巡检线路"],
        ["stationId", "场站"],
        ["inspectionType", "巡检类型"],
        ["status", "状态"],
        ["pointCount", "巡检点数"],
        ["times", "触发时点"],
      ],
      defects: [
        ["code", "缺陷单号"],
        ["stationId", "场站"],
        ["sourceType", "来源"],
        ["status", "状态"],
        ["level", "类别"],
        ["description", "缺陷描述"],
        ["deviceId", "设备"],
        ["createdAt", "提交时间"],
      ],
      workTickets: [
        ["code", "工作票号"],
        ["subtype", "票种"],
        ["status", "状态"],
        ["description", "工作内容"],
        ["deviceName", "设备名称"],
        ["leaderId", "工作负责人"],
        ["revision", "版本"],
      ],
      operationTickets: [
        ["code", "操作票号"],
        ["firstType", "一级类型"],
        ["secondType", "二级类型"],
        ["status", "状态"],
        ["description", "操作任务"],
        ["operatorId", "操作人"],
        ["supervisorId", "监护人"],
      ],
      orders: [
        ["code", "工单编号"],
        ["name", "任务名称"],
        ["stationId", "场站"],
        ["type", "类型"],
        ["status", "状态"],
        ["assigneeId", "负责人"],
        ["plannedAt", "计划触发"],
        ["dueAt", "完成期限"],
      ],
      notices: [
        ["code", "通知单号"],
        ["stationId", "场站"],
        ["issuerId", "发令人"],
        ["recipientId", "收令人"],
        ["description", "通知内容"],
        ["createdAt", "发令时间"],
      ],
      commands: [
        ["code", "调度单号"],
        ["status", "状态"],
        ["issuerId", "发令人"],
        ["recipientId", "收令人"],
        ["executorId", "执行人"],
        ["repliedBy", "回令人"],
        ["receivedAt", "得令时间"],
        ["createdAt", "发令时间"],
      ],
      workHazards: [
        ["code", "危险点编号"],
        ["name", "标题"],
        ["analysis", "危险点辨识分析"],
        ["measures", "预控措施"],
      ],
      operationHazards: [
        ["code", "危险点编号"],
        ["risk", "危险点"],
        ["consequence", "危险后果"],
        ["measures", "控制措施"],
      ],
      duties: [
        ["year", "年份"],
        ["stationId", "场站"],
        ["dutyType", "职责类型"],
        ["personId", "姓名"],
      ],
      messages: [
        ["name", "消息内容"],
        ["recipient", "接收人 / 职责"],
        ["createdAt", "时间"],
        ["read", "消息已读"],
      ],
    })[props.kind] || [
      ["code", "编号"],
      ["name", "名称"],
      ["description", "内容"],
      ["createdAt", "时间"],
    ]
  ).concat(
    view.value === "processed"
      ? [
          ["auditTime", "处理时间"],
          ["auditAction", "处理结果"],
          ["auditActor", "处理人"],
        ]
      : [],
  ),
);
function display(r, k) {
  if (k === "deviceCount") return r.deviceIds?.length || 0;
  if (k === "pointCount") return r.points?.length || 0;
  if (k === "handoverState")
    return r.handover
      ? "已填写"
      : r.endAt <= props.data.now
        ? "待补填"
        : "班内待填";
  let v = r[k];
  if (k === "status") return st(v);
  if (["type", "sourceType", "subtype"].includes(k)) return typeNames[v] || v;
  if (k.endsWith("Id") || k.endsWith("By") || k === "auditActor")
    return names(v);
  if (k === "read") return v ? "已读" : "未读";
  return Array.isArray(v) ? v.join("、") : String(v ?? "—").replace("T", " ");
}
function reset() {
  query.value = "";
  station.value = "";
  status.value = "";
  type.value = "";
  person.value = "";
  year.value = "";
  dates.value = [];
}
</script>
<template>
  <section class="panel table-panel">
    <el-tabs v-if="tabs.length" v-model="view"
      ><el-tab-pane
        v-for="t in tabs"
        :key="t[0]"
        :name="t[0]"
        :label="t[1]" /></el-tabs
    ><el-alert
      v-if="approval"
      title="“待我处理”依据班次、年度职责与票上人员确定；切换“全部待办”可统一演示，未实施权限隔离。"
      type="info"
      :closable="false"
      class="mb"
    />
    <div class="filters">
      <el-input
        v-model="query"
        clearable
        placeholder="编号 / 内容 / 设备 / 人员"
        style="width: 260px"
      /><el-select
        v-model="station"
        clearable
        placeholder="全部场站"
        style="width: 220px"
        ><el-option
          v-for="s in data.stations"
          :key="s.id"
          :value="s.id"
          :label="s.name" /></el-select
      ><el-select
        v-model="type"
        clearable
        placeholder="类型 / 来源"
        style="width: 185px"
        ><el-option
          v-for="t in [...new Set(source.map(classify).filter(Boolean))]"
          :key="t"
          :value="t"
          :label="typeNames[t] || t" /></el-select
      ><el-select
        v-model="status"
        clearable
        placeholder="全部状态"
        style="width: 185px"
        ><el-option
          v-for="s in [...new Set(source.map((x) => x.status).filter(Boolean))]"
          :key="s"
          :value="s"
          :label="st(s)" /></el-select
      ><el-select
        v-model="person"
        clearable
        filterable
        placeholder="负责人 / 人员"
        style="width: 185px"
        ><el-option
          v-for="p in data.people"
          :key="p.id"
          :value="p.id"
          :label="p.name" /></el-select
      ><el-input
        v-if="kind === 'duties'"
        v-model="year"
        placeholder="年份"
        clearable
        style="width: 110px"
      /><el-date-picker
        v-model="dates"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px; flex-grow: 0"
      /><el-button @click="reset">重置</el-button>
    </div>
    <div class="list-meta">
      共 {{ filtered.length }} 条
      <span v-if="approval && view === 'mine' && !filtered.length"
        >当前身份无待办，可切换全部待办或右上角业务身份。</span
      >
    </div>
    <el-table
      :data="paged"
      border
      stripe
      class="click-table"
      @row-click="(r) => emit('view', r)"
      ><el-table-column
        type="index"
        label="序号"
        width="65"
        :index="(i) => (pageNo - 1) * size + i + 1"
      /><el-table-column
        v-for="c in columns"
        :key="c[0]"
        :label="c[1]"
        :min-width="
          [
            'description',
            'analysis',
            'risk',
            'measures',
            'name',
            'consequence',
            'skill',
          ].includes(c[0])
            ? 240
            : c[0].endsWith('At') || c[0] === 'auditTime'
              ? 170
              : 140
        "
        show-overflow-tooltip
        ><template #default="{ row }"
          ><el-tag
            v-if="c[0] === 'status'"
            :type="
              [
                'closed',
                'completed',
                'repaired',
                'terminated',
                'active',
              ].includes(row.status)
                ? 'success'
                : ['returned', 'void'].includes(row.status)
                  ? 'danger'
                  : 'info'
            "
            >{{ display(row, c[0]) }}</el-tag
          ><span v-else>{{ display(row, c[0]) }}</span
          ><small
            v-if="
              c[0] === 'dueAt' &&
              row.dueAt < data.now &&
              !['closed', 'completed'].includes(row.status)
            "
            class="hot"
            >已超时</small
          ></template
        ></el-table-column
      ><el-table-column
        label="操作"
        fixed="right"
        :width="kind.endsWith('Rules') ? 190 : 85"
        ><template #default="{ row }"
          ><el-button link type="primary" @click.stop="emit('view', row)"
            >查看</el-button
          ><template v-if="kind.endsWith('Rules')"
            ><el-button link @click.stop="emit('preview', row)">预览</el-button
            ><el-button link @click.stop="emit('toggle', row)">{{
              row.status === "active" ? "停用" : "恢复"
            }}</el-button></template
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      class="mt"
      v-model:current-page="pageNo"
      v-model:page-size="size"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="filtered.length"
    />
  </section>
</template>
