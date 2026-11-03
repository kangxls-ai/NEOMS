<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { statusNames } from "./schema";

const props = defineProps({
  data: Object,
  user: Object,
  initialShiftId: String,
});
const emit = defineEmits(["view", "changed"]);
const stationId = ref(""),
  date = ref(""),
  shiftId = ref(""),
  events = ref([]),
  defects = ref([]),
  loading = ref(false);
const noteDialog = ref(false),
  note = ref(""),
  handoverDialog = ref(false),
  handover = ref(""),
  saving = ref(false);
const noteDetails = ref(null);
const noteDetailOpen = computed({
  get: () => !!noteDetails.value,
  set: (open) => {
    if (!open) noteDetails.value = null;
  },
});
function viewEvent(row) {
  if (row.kind === "notes") {
    noteDetails.value = (props.data.notes || []).find(
      (n) => n.id === row.recordId,
    ) || { description: row.content, createdAt: row.time };
  } else emit("view", row.recordId);
}
let loadSequence = 0;
const allShifts = computed(() => props.data.shifts || []);
const choices = computed(() =>
  allShifts.value
    .filter(
      (s) =>
        s.stationId === stationId.value &&
        s.startAt?.slice(0, 10) === date.value,
    )
    .toSorted((a, b) => a.startAt.localeCompare(b.startAt)),
);
const shift = computed(() => choices.value.find((s) => s.id === shiftId.value));
const previous = computed(() =>
  shift.value
    ? allShifts.value
        .filter(
          (s) =>
            s.stationId === stationId.value && s.endAt <= shift.value.startAt,
        )
        .toSorted((a, b) => b.endAt.localeCompare(a.endAt))[0]
    : null,
);
const isActive = computed(
  () =>
    shift.value &&
    shift.value.startAt <= props.data.now &&
    shift.value.endAt > props.data.now,
);
const hasEnded = computed(
  () => shift.value && shift.value.endAt <= props.data.now,
);
const names = (id) =>
  (props.data.people || []).find((p) => p.id === id)?.name || id || "—";
const stationName = computed(
  () =>
    (props.data.stations || []).find((s) => s.id === stationId.value)?.name ||
    "—",
);
const members = computed(
  () => (shift.value?.memberIds || []).map(names).join("、") || "未配置",
);
const time = (value) =>
  value ? String(value).replace("T", " ").slice(0, 19) : "—";
function timeRange(s) {
  if (!s) return "—";
  return `${s.startAt.slice(11, 16)}　－　${s.startAt.slice(0, 10) !== s.endAt.slice(0, 10) ? "次日 " : ""}${s.endAt.slice(11, 16)}`;
}
function selectShift() {
  if (!choices.value.some((s) => s.id === shiftId.value)) {
    shiftId.value =
      choices.value.find(
        (s) => s.startAt <= props.data.now && s.endAt > props.data.now,
      )?.id ||
      choices.value[0]?.id ||
      "";
  }
}
watch(
  () => props.data,
  () => {
    if (!stationId.value) {
      const requested =
        allShifts.value.find((s) => s.id === props.initialShiftId) ||
        allShifts.value.find(
          (s) => s.startAt <= props.data.now && s.endAt > props.data.now,
        );
      stationId.value =
        requested?.stationId || props.data.stations?.[0]?.id || "";
      date.value =
        requested?.startAt.slice(0, 10) || props.data.now?.slice(0, 10) || "";
      shiftId.value = requested?.id || "";
    }
    selectShift();
  },
  { immediate: true },
);
watch(choices, selectShift);
async function request(url, method = "GET", body) {
  const response = await fetch("/api" + url, {
    method,
    credentials: "same-origin",
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
  const result = await response.json();
  if (!response.ok) throw Error(result.message || "请求失败");
  return result;
}
async function load() {
  const sequence = ++loadSequence;
  events.value = [];
  defects.value = [];
  if (!shiftId.value) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const sheet = await request("/shift-logs/" + shiftId.value);
    if (sequence === loadSequence) {
      events.value = sheet.events;
      defects.value = sheet.defects;
    }
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}
watch([shiftId, () => props.data], load, { immediate: true });
function openHandover() {
  handover.value = shift.value.handover || shift.value.handoverDraft || "";
  handoverDialog.value = true;
}
async function saveNote() {
  if (!note.value.trim()) {
    ElMessage.warning("请填写值班记事内容");
    return;
  }
  saving.value = true;
  try {
    await request("/entities/notes", "POST", {
      name: "值班记事",
      stationId: stationId.value,
      shiftId: shiftId.value,
      description: note.value.trim(),
    });
    noteDialog.value = false;
    note.value = "";
    emit("changed");
    await load();
    ElMessage.success("值班记事已保存");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
async function saveHandover() {
  if (!handover.value.trim()) {
    ElMessage.warning("请填写班后交代内容");
    return;
  }
  saving.value = true;
  try {
    await request(
      `/records/${shiftId.value}/actions/${hasEnded.value ? "handover" : "handoverDraft"}`,
      "POST",
      { handover: handover.value.trim() },
    );
    handoverDialog.value = false;
    emit("changed");
    ElMessage.success(hasEnded.value ? "班后交代已提交" : "班后交代草稿已保存");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="current-log-page">
    <div class="page-heading">
      <div><h1>{{ initialShiftId ? "值班日志" : "当前日志" }}</h1><p>查看班次运行情况，记录值班事项与交接内容。</p></div>
      <div class="inline-actions">
        <el-button :disabled="!shift || (!isActive && !hasEnded)" @click="openHandover">班后交代</el-button>
        <el-button type="primary" :disabled="!isActive" @click="note = ''; noteDialog = true">值班记事</el-button>
      </div>
    </div>
    <header class="panel current-log-toolbar">
      <div class="current-log-filters">
        <label
          ><span>场站</span
          ><el-select
            v-model="stationId"
            aria-label="场站"
            placeholder="请选择场站"
            ><el-option
              v-for="s in data.stations"
              :key="s.id"
              :value="s.id"
              :label="s.name" /></el-select
        ></label>
        <label
          ><span>日期</span
          ><el-date-picker
            v-model="date"
            aria-label="日期"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :clearable="false"
            placeholder="请选择日期"
        /></label>
        <label
          ><span>班次</span
          ><el-select
            v-model="shiftId"
            aria-label="班次"
            placeholder="当天未排班"
            ><el-option
              v-for="s in choices"
              :key="s.id"
              :value="s.id"
              :label="s.name"
              ><span>{{ s.name }}</span
              ><small class="current-log-shift-option">{{
                timeRange(s)
              }}</small></el-option
            ></el-select
          ></label
        >
      </div>
    </header>
    <div class="current-log-body" v-loading="loading">
      <section class="panel current-log-section current-log-duty">
        <h2>值班信息</h2>
        <div class="current-log-info">
          <div>
            <label>场站：</label><span>{{ stationName }}</span>
          </div>
          <div>
            <label>日期：</label><span>{{ date || "—" }}</span>
          </div>
          <div>
            <label>班次：</label><span>{{ shift?.name || "—" }}</span>
          </div>
          <div>
            <label>值班负责人：</label
            ><span>{{ names(shift?.assigneeId) }}</span>
          </div>
          <div>
            <label>值班人员：</label><span>{{ shift ? members : "—" }}</span>
          </div>
          <div class="current-log-time">
            <label>班次时间：</label><span>{{ timeRange(shift) }}</span>
          </div>
        </div>
      </section>
      <section class="panel current-log-section current-log-before">
        <h2>班前交代</h2>
        <p v-if="previous?.handover" class="current-log-handover-text">
          {{ previous.handover }}
        </p>
        <p v-else class="current-log-no-handover">
          {{
            !shift
              ? "当前日期暂无班次"
              : previous
                ? "上一班班后交代待补填"
                : "暂无上一班交代"
          }}
        </p>
      </section>
      <section class="panel current-log-section current-log-events">
        <h2>值班情况</h2>
        <div class="current-log-table-wrap">
          <el-table
            :data="events"
            height="220"
            empty-text="本班暂无值班记录"
            @row-dblclick="viewEvent"
          >
            <el-table-column
              prop="type"
              label="类型"
              width="180"
              align="center"
            />
            <el-table-column label="时间" width="220" align="center"
              ><template #default="{ row }">{{
                time(row.time)
              }}</template></el-table-column
            >
            <el-table-column
              prop="content"
              label="内容"
              min-width="380"
              header-align="center"
              show-overflow-tooltip
            />
            <el-table-column label="操作" width="160" align="center"
              ><template #default="{ row }"
                ><el-button link type="primary" @click="viewEvent(row)"
                  >查看详情</el-button
                ></template
              ></el-table-column
            >
          </el-table>
        </div>
      </section>
      <section class="panel current-log-section current-log-defects">
        <h2>缺陷记录</h2>
        <div class="current-log-table-wrap">
          <el-table
            :data="defects"
            max-height="300"
            empty-text="本班暂无新增缺陷"
            @row-dblclick="(r) => emit('view', r.id)"
          >
            <el-table-column label="状态" min-width="80" align="center"
              ><template #default="{ row }">{{
                statusNames[row.status] || row.status
              }}</template></el-table-column
            >
            <el-table-column
              prop="code"
              label="缺陷单号"
              min-width="170"
              align="center"
              ><template #default="{ row }"
                ><el-button
                  link
                  class="current-log-defect-code" type="primary"
                  @click="emit('view', row.id)"
                  >{{ row.code }}</el-button
                ></template
              ></el-table-column
            >
            <el-table-column
              prop="reporterName"
              label="发现人"
              min-width="80"
              align="center"
            />
            <el-table-column
              prop="level"
              label="缺陷等级"
              min-width="100"
              align="center"
            />
            <el-table-column label="设备名称" min-width="170" align="center"
              ><template #default="{ row }">{{
                data.devices?.find((d) => d.id === row.deviceId)?.name || "—"
              }}</template></el-table-column
            >
            <el-table-column label="发现时间" min-width="160" align="center"
              ><template #default="{ row }">{{
                time(row.discoveredAt)
              }}</template></el-table-column
            >
            <el-table-column label="审核提交时间" min-width="160" align="center"
              ><template #default="{ row }">{{
                time(row.approvalSubmittedAt)
              }}</template></el-table-column
            >
          </el-table>
        </div>
      </section>
    </div>
    <el-dialog
      v-model="noteDialog"
      title="值班记事"
      width="640px"
      destroy-on-close
      ><el-form label-position="top"
        ><el-form-item label="场站 / 日期 / 班次"
          ><div>
            {{ stationName }} / {{ date }} / {{ shift?.name }}
          </div></el-form-item
        ><el-form-item label="值班记事内容"
          ><el-input
            v-model="note"
            type="textarea"
            :rows="8"
            maxlength="4000"
            show-word-limit
            placeholder="请填写值班期间的运行情况和处理事项" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="noteDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveNote"
          >提交</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="noteDetailOpen"
      title="值班记事详情"
      width="680px"
      destroy-on-close
      ><el-descriptions :column="2" border
        ><el-descriptions-item label="场站">{{
          stationName
        }}</el-descriptions-item
        ><el-descriptions-item label="日期">{{ date }}</el-descriptions-item
        ><el-descriptions-item label="班次">{{
          shift?.name
        }}</el-descriptions-item
        ><el-descriptions-item label="记录人">{{
          names(noteDetails?.createdBy)
        }}</el-descriptions-item
        ><el-descriptions-item label="填写时间" :span="2">{{
          time(noteDetails?.createdAt)
        }}</el-descriptions-item></el-descriptions
      >
      <h3 class="mt">值班记事内容</h3>
      <p class="current-log-handover-text">{{ noteDetails?.description }}</p>
      <template #footer
        ><el-button @click="noteDetails = null">关闭</el-button></template
      ></el-dialog
    >
    <el-dialog
      v-model="handoverDialog"
      title="班后交代"
      width="700px"
      destroy-on-close
      ><el-form label-position="top"
        ><el-form-item label="场站 / 日期 / 班次"
          ><div>
            {{ stationName }} / {{ date }} / {{ shift?.name }}（{{
              timeRange(shift)
            }}）
          </div></el-form-item
        ><el-form-item label="班后交代内容"
          ><el-input
            v-model="handover"
            type="textarea"
            :rows="9"
            maxlength="6000"
            show-word-limit
            placeholder="请填写待跟踪事项、设备状态和下一班注意事项"
        /></el-form-item>
        <p v-if="isActive" class="current-log-dialog-tip">
          当班期间可保存草稿；班次结束后提交，作为下一班的班前交代。
        </p></el-form
      ><template #footer
        ><el-button @click="handoverDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveHandover">{{
          hasEnded ? "提交" : "保存草稿"
        }}</el-button></template
      ></el-dialog
    >
  </div>
</template>

<style>
.current-log-toolbar { margin-bottom: 20px; }
.current-log-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 20px 28px; }
.current-log-filters > label { display: flex; gap: 12px; align-items: center; }
.current-log-filters > label > span { color: #707b8e; font-size: 13px; white-space: nowrap; }
.current-log-filters .el-select, .current-log-filters .el-date-editor { width: 210px; }
.current-log-section h2 { font-size: 15px; font-weight: 600; color: #3c4b63; margin: 0 0 20px; }
.current-log-info { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 22px 28px; }
.current-log-info > div { display: flex; gap: 12px; line-height: 24px; min-width: 0; }
.current-log-info label { color: #788395; font-size: 13px; white-space: nowrap; flex: 0 0 90px; }
.current-log-info span { overflow-wrap: anywhere; }
.current-log-handover-text { white-space: pre-wrap; line-height: 1.9; margin: 0; }
.current-log-no-handover { color: #929daf; font-size: 13px; margin: 0; }
.current-log-shift-option { float: right; color: #99a2b0; margin-left: 14px; }
.current-log-dialog-tip { color: #8b94a4; font-size: 13px; }
@media(max-width:1100px) {
  .current-log-info { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .current-log-filters { gap: 16px; }
  .current-log-filters .el-select, .current-log-filters .el-date-editor { width: 180px; }
}
@media(max-width:700px) {
  .current-log-page .page-heading { align-items: flex-start; flex-wrap: wrap; }
  .current-log-info { grid-template-columns: 1fr; }
  .current-log-filters > label { width: 100%; }
  .current-log-filters .el-select, .current-log-filters .el-date-editor { flex: 1; width: auto; }
}
</style>
