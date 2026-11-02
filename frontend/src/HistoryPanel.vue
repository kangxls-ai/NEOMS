<script setup>
import { computed, ref } from "vue";
import { statusNames, typeNames, labels, fields } from "./schema";
const props = defineProps({ history: Array, record: Object, data: Object });
const selected = ref(0);
const snapshot = computed(() =>
  JSON.parse(props.history[selected.value]?.snapshot || "{}"),
);
const names = (id) =>
  Object.values(props.data)
    .filter(Array.isArray)
    .flat()
    .find((x) => x.id === id)?.name ||
  id ||
  "—";
function show(v, key) {
  if (v === undefined) return "—";
  if (key === "status") return statusNames[v] || v;
  if (["type", "subtype", "sourceType"].includes(key)) return typeNames[v] || v;
  if (typeof v === "boolean") return v ? "是" : "否";
  if (key.endsWith("Id") || key.endsWith("By")) return names(v);
  if (Array.isArray(v))
    return v
      .map((x) =>
        typeof x === "object"
          ? x.name || x.analysis || x.risk || x.description || "记录"
          : names(x),
      )
      .join("；");
  return typeof v === "object" ? JSON.stringify(v) : String(v);
}
const diff = computed(() =>
  [...new Set([...Object.keys(snapshot.value), ...Object.keys(props.record)])]
    .filter(
      (k) =>
        !k.startsWith("_") &&
        ![
          "id",
          "kind",
          "samples",
          "processRecords",
          "hazards",
          "measures",
          "items",
          "ruleSnapshot",
          "defectSnapshot",
        ].includes(k),
    )
    .map((k) => ({
      key: k,
      label:
        (fields[props.record.kind] || []).find((f) => f.key === k)?.label ||
        labels[k] ||
        {
          name: "标题",
          description: "内容",
          memberIds: "班组成员",
          orderIds: "关联工单",
          attachmentIds: "附件",
        }[k] ||
        k,
      old: show(snapshot.value[k], k),
      current: show(props.record[k], k),
      changed:
        JSON.stringify(snapshot.value[k]) !== JSON.stringify(props.record[k]),
    })),
);
function print(draft) {
  window.open(
    `/api/print/${props.record.id}?historyId=${props.history[selected.value].id}&draft=${draft}`,
    "_blank",
    "noopener",
  );
}
</script>
<template>
  <el-empty v-if="!history.length" description="暂无历史记录" /><template v-else
    ><div class="section-title">历史版本与当前记录对照</div>
    <el-select v-model="selected" class="mb"
      ><el-option
        v-for="(h, i) in history"
        :key="h.id || i"
        :value="i"
        :label="`${h.time} · ${h.action} · ${names(h.actor)}`"
    /></el-select>
    <div class="inline-actions mb" v-if="record.kind.endsWith('Tickets')">
      <el-tag
        >历史票据版本 {{ snapshot.revision || 1 }} ·
        {{ statusNames[snapshot.status] }}</el-tag
      ><el-button @click="print(true)">历史草稿预览</el-button
      ><el-button
        :disabled="!['executing', 'terminated'].includes(snapshot.status)"
        @click="print(false)"
        >历史正式打印 / PDF</el-button
      >
    </div>
    <el-table :data="diff" border
      ><el-table-column prop="label" label="字段" width="170" /><el-table-column
        prop="old"
        label="选中时刻的历史快照"
        min-width="240"
      /><el-table-column label="当前记录" min-width="240"
        ><template #default="{ row }"
          ><span :class="{ hot: row.changed }">{{ row.current }}</span
          ><el-tag v-if="row.changed" size="small" type="warning" class="mr"
            >已变化</el-tag
          ></template
        ></el-table-column
      ></el-table
    ><template
      v-for="key in ['measures', 'hazards', 'items', 'processRecords']"
      :key="key"
      ><template v-if="snapshot[key]?.length"
        ><div class="section-title">
          历史{{
            {
              measures: "安全措施",
              hazards: "危险点快照",
              items: "执行项目",
              processRecords: "过程登记",
            }[key]
          }}
        </div>
        <el-table :data="snapshot[key]"
          ><el-table-column label="分组 / 内容"
            ><template #default="{ row }"
              >{{ row.group || row.type }}
              {{
                row.name || row.analysis || row.risk || row.description
              }}</template
            ></el-table-column
          ><el-table-column
            prop="measures"
            label="预控措施"
            v-if="key === 'hazards'"
          /><el-table-column label="执行 / 登记时间"
            ><template #default="{ row }">{{
              row.executedAt || row.eventAt || row.time || "—"
            }}</template></el-table-column
          ></el-table
        ></template
      ></template
    >
    <div class="section-title">全部流转记录</div>
    <el-timeline
      ><el-timeline-item
        v-for="(h, i) in history"
        :key="h.id || i"
        :timestamp="h.time"
        ><el-button link type="primary" @click="selected = i">{{
          h.action
        }}</el-button>
        <p>{{ names(h.actor) }}</p></el-timeline-item
      ></el-timeline
    ></template
  >
</template>
