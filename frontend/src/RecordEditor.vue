<script setup>
import { computed, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";
import FormFields from "./FormFields.vue";
import { fields } from "./schema";
import { operationTemplate } from "./operationTemplates";
const props = defineProps({
  model: Object,
  kind: String,
  data: Object,
  user: Object,
});
const emit = defineEmits(["upload", "preview"]);
const pointIndex = ref(0);
const selectedHazards = ref((props.model.hazards || []).map((x) => x.id));
let restoringSubtype = false;
watch(
  () => props.model.subtype,
  async (next, old) => {
    if (restoringSubtype) {
      restoringSubtype = false;
      return;
    }
    if (
      props.kind !== "workTickets" ||
      !old ||
      !(props.model.measures || []).length
    )
      return;
    try {
      await ElMessageBox.confirm(
        "切换票种会清空当前安全措施，基础信息与危险点保留。是否继续？",
        "切换票种",
        { type: "warning" },
      );
      props.model.measures = [];
    } catch {
      restoringSubtype = true;
      props.model.subtype = old;
    }
  },
);
const isRule = computed(() => props.kind.endsWith("Rules"));
const isTicket = computed(() => props.kind.endsWith("Tickets"));
const groupNames = computed(
  () =>
    ({
      electrical1: [
        "应拉断路器、隔离开关",
        "接地线、接地刀闸（地点名称编号）",
        "标示牌、围栏、挡板及二次回路措施",
        "检修人员执行安全措施",
      ],
      electrical2: [
        "工作条件及保留带电设备",
        "安全注意事项",
        "标示牌、围栏、挡板及二次回路措施",
        "检修人员执行安全措施",
      ],
      wind: [
        "运行人员安全措施",
        "检修及机械隔离措施",
        "接地线、接地刀闸",
        "挂牌 / 上锁",
      ],
      contact: [],
    })[props.model.subtype] || [],
);
const point = computed(() => props.model.points?.[pointIndex.value]);
watch(
  point,
  (p) => {
    if (p && !p.deviceIds) p.deviceIds = p.deviceId ? [p.deviceId] : [];
  },
  { immediate: true },
);
function addMeasure() {
  (props.model.measures ||= []).push({
    id: crypto.randomUUID(),
    group: groupNames.value[0] || "安全措施",
    name: "",
    executed: false,
  });
}
function addPoint() {
  (props.model.points ||= []).push({
    id: crypto.randomUUID(),
    name: "新巡检点",
    deviceIds: [],
    items: [],
  });
  pointIndex.value = props.model.points.length - 1;
}
function addItem() {
  point.value.items.push({
    id: crypto.randomUUID(),
    name: "",
    type: "record",
    unit: "",
  });
}
function hazards() {
  let source =
    props.kind === "operationTickets"
      ? props.data.operationHazards
      : props.data.workHazards;
  props.model.hazards = source
    .filter((x) => selectedHazards.value.includes(x.id))
    .map((x) => ({ ...x }));
}
function deletePoint() {
  props.model.points.splice(pointIndex.value, 1);
  pointIndex.value = 0;
}
const ranges = [
  {
    key: "months",
    label: "月份",
    values: Array.from({ length: 12 }, (_, i) => ["" + (i + 1), i + 1 + "月"]),
  },
  {
    key: "weeks",
    label: "月内周次",
    values: [
      ["1", "第一周"],
      ["2", "第二周"],
      ["3", "第三周"],
      ["4", "第四周"],
      ["5", "第五周"],
      ["6", "第六周"],
      ["last", "最后一周"],
    ],
  },
  {
    key: "weekdays",
    label: "星期",
    values: ["一", "二", "三", "四", "五", "六", "日"].map((v, i) => [
      "" + (i + 1),
      "周" + v,
    ]),
  },
  {
    key: "days",
    label: "日号",
    values: Array.from({ length: 31 }, (_, i) => ["" + (i + 1), i + 1 + "日"]),
  },
];
watch(
  () => props.model.firstType,
  () => {
    if (props.kind !== "operationTickets") return;
    const allowed =
      props.model.firstType === "PT操作票"
        ? ["运行转检修", "检修转运行"]
        : props.model.firstType === "集电线路操作票"
          ? null
          : [
              "运行转检修",
              "检修转运行",
              "运行转冷备用",
              "冷备用转检修",
              "检修转冷备用",
              "冷备用转运行",
            ];
    if (allowed && !allowed.includes(props.model.secondType))
      props.model.secondType = "";
  },
);
async function loadTemplate() {
  if (props.model.items?.length) {
    try {
      await ElMessageBox.confirm(
        "载入模板将替换当前未提交的操作项目；可继续编辑、增删与排序。",
        "载入模板",
      );
    } catch {
      return;
    }
  }
  props.model.items = operationTemplate(
    props.model.firstType,
    props.model.secondType,
    props.model.deviceName,
  );
}
</script>
<template>
  <el-form label-position="top"
    ><div class="section-title">基础信息与关联</div>
    <FormFields
      :fields="fields[kind] || []"
      :model="model"
      :data="data"
      :user="user"
      :kind="kind"
    />
    <template v-if="kind === 'inspectionRules'"
      ><div class="section-title">线路 / 巡检点设置</div>
      <div class="point-editor">
        <div class="point-tree">
          <div
            v-for="(p, i) in model.points"
            :key="p.id"
            :class="{ selected: i === pointIndex }"
            @click="pointIndex = i"
          >
            {{ i + 1 }}. {{ p.name }}
          </div>
          <el-button @click="addPoint">添加巡检点</el-button>
        </div>
        <div v-if="point" class="point-content">
          <el-input v-model="point.name" placeholder="巡检点名称" /><el-select
            v-model="point.deviceIds"
            multiple
            filterable
            placeholder="选择本点设备"
            ><el-option
              v-for="d in (data.devices || []).filter(
                (x) => !model.stationId || x.stationId === model.stationId,
              )"
              :key="d.id"
              :label="d.name + ' · ' + d.deviceCode"
              :value="d.id" /></el-select
          ><el-table :data="point.items"
            ><el-table-column label="适用设备" width="210"
              ><template #default="{ row }"
                ><el-select
                  v-model="row.deviceId"
                  clearable
                  placeholder="本点全部设备"
                  ><el-option
                    v-for="id in point.deviceIds"
                    :key="id"
                    :value="id"
                    :label="
                      data.devices.find((x) => x.id === id)?.name || id
                    " /></el-select></template></el-table-column
            ><el-table-column label="巡检项名称"
              ><template #default="{ row }"
                ><el-input v-model="row.name" /></template></el-table-column
            ><el-table-column label="类型" width="140"
              ><template #default="{ row }"
                ><el-select v-model="row.type"
                  ><el-option label="记录" value="record" /><el-option
                    label="观察"
                    value="observation" /></el-select></template></el-table-column
            ><el-table-column label="单位" width="100"
              ><template #default="{ row }"
                ><el-input v-model="row.unit" /></template></el-table-column
            ><el-table-column width="70"
              ><template #default="{ $index }"
                ><el-button
                  link
                  type="danger"
                  @click="point.items.splice($index, 1)"
                  >删除</el-button
                ></template
              ></el-table-column
            ></el-table
          >
          <div class="inline-actions">
            <el-button @click="addItem">添加巡检项</el-button
            ><el-button type="danger" plain @click="deletePoint"
              >删除巡检点</el-button
            >
          </div>
        </div>
        <el-empty v-else description="请先添加巡检点" /></div
    ></template>
    <template v-if="isRule"
      ><div class="section-title">时间交集与触发预览</div>
      <el-alert
        title="以下条件共同满足时触发；不选择表示该维度不限。月内周次按周一开始的自然周与本月相交计算。"
        type="info"
        :closable="false"
      />
      <div class="form-grid">
        <el-form-item v-for="r in ranges" :key="r.key" :label="r.label"
          ><el-select
            v-model="model[r.key]"
            multiple
            clearable
            collapse-tags
            collapse-tags-tooltip
            ><el-option
              v-for="v in r.values"
              :key="v[0]"
              :value="v[0]"
              :label="v[1]" /></el-select
        ></el-form-item>
      </div>
      <div class="time-row">
        <label>开始时间</label>
        <div v-for="(_, i) in model.times" :key="i">
          <el-time-picker
            v-model="model.times[i]"
            value-format="HH:mm"
            format="HH:mm"
          /><el-button link type="danger" @click="model.times.splice(i, 1)"
            >移除</el-button
          >
        </div>
        <el-button @click="(model.times ||= []).push('08:00')"
          >添加时点</el-button
        ><el-button type="primary" plain @click="emit('preview', model)"
          >预览未来触发 / 负责人</el-button
        >
      </div></template
    >
    <template v-if="isTicket"
      ><template v-if="kind === 'workTickets' && model.subtype !== 'contact'"
        ><div class="section-title">本票种安全措施</div>
        <el-table :data="model.measures || []"
          ><el-table-column label="措施分组" width="300"
            ><template #default="{ row }"
              ><el-select v-model="row.group" filterable allow-create
                ><el-option
                  v-for="g in groupNames"
                  :key="g"
                  :label="g"
                  :value="g" /></el-select></template></el-table-column
          ><el-table-column label="措施内容"
            ><template #default="{ row }"
              ><el-input
                v-model="row.name"
                type="textarea"
                :rows="2" /></template></el-table-column
          ><el-table-column width="70"
            ><template #default="{ $index }"
              ><el-button
                link
                type="danger"
                @click="model.measures.splice($index, 1)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        ><el-button @click="addMeasure">添加措施行</el-button></template
      >
      <template v-if="kind === 'operationTickets'"
        ><div class="section-title">操作任务 / 顺序项目</div>
        <el-alert
          title="模板：集电线路运行转检修保留原型13项，文字不完整处单独标注；其他转换为待补占位，不作为正式操作规程。"
          :closable="false"
          type="info"
        /><el-button class="mt mb" type="primary" plain @click="loadTemplate"
          >载入所选类型模板</el-button
        ><el-table :data="model.items || []"
          ><el-table-column
            label="序号"
            type="index"
            width="65"
          /><el-table-column label="操作项目"
            ><template #default="{ row }"
              ><el-input
                v-model="row.name"
                type="textarea"
                :rows="2" /></template></el-table-column
          ><el-table-column width="100"
            ><template #default="{ $index }"
              ><el-button
                link
                @click="[
                  $index > 0 &&
                    ([model.items[$index - 1], model.items[$index]] = [
                      model.items[$index],
                      model.items[$index - 1],
                    ]),
                ]"
                >上移</el-button
              ><el-button
                link
                type="danger"
                @click="model.items.splice($index, 1)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        ><el-button
          @click="
            (model.items ||= []).push({
              id: crypto.randomUUID(),
              name: '',
              executed: false,
            })
          "
          >添加项目</el-button
        ></template
      >
      <div class="section-title">危险点与预控措施（引用快照）</div>
      <el-select
        v-model="selectedHazards"
        multiple
        filterable
        placeholder="从对应危险点库选择，选择后保存快照"
        @change="hazards"
        ><el-option
          v-for="h in (kind === 'operationTickets'
            ? data.operationHazards
            : data.workHazards) || []"
          :key="h.id"
          :value="h.id"
          :label="h.name + ' · ' + (h.analysis || h.risk)" /></el-select
      ><el-table :data="model.hazards || []"
        ><el-table-column label="辨识分析 / 危险点"
          ><template #default="{ row }">{{
            row.analysis || row.risk
          }}</template></el-table-column
        ><el-table-column
          label="危险后果"
          v-if="kind === 'operationTickets'"
          prop="consequence" /><el-table-column
          label="预控 / 控制措施"
          prop="measures" /></el-table
    ></template>
    <template v-if="kind === 'defects'"
      ><div class="section-title">缺陷照片与证据</div>
      <slot name="evidence"
    /></template>
  </el-form>
</template>
