<script setup>
import { computed } from "vue";
const props = defineProps({
  model: Object,
  fields: Array,
  data: Object,
  user: Object,
  kind: String,
});
function opts(f) {
  if (f.type === "source") {
    let a = ["warning", "alarm"].includes(props.model.sourceType)
      ? props.data.alerts
      : props.data.orders;
    return (a || [])
      .filter(
        (x) => !props.model.stationId || x.stationId === props.model.stationId,
      )
      .filter((x) =>
        ["warning", "alarm"].includes(props.model.sourceType)
          ? x.level === props.model.sourceType
          : x.type === props.model.sourceType,
      )
      .map((x) => ({
        label: `${x.code} · ${x.name}`,
        value: x.id,
      }));
  }
  if (["ref", "multiRef"].includes(f.type)) {
    let list = (props.data[f.options] || []).filter(
      (x) => x.status !== "removed",
    );
    if (props.kind === "workTickets" && f.key === "leaderId") {
      let eligible = (props.data.duties || [])
        .filter(
          (x) =>
            x.status !== "removed" &&
            String(x.year) === (props.data.now || "2026").slice(0, 4) &&
            x.stationId === props.model.stationId &&
            x.dutyType === "工作负责人",
        )
        .map((x) => x.personId);
      list = list.filter(
        (x) => eligible.includes(x.id) || x.id === props.model.leaderId,
      );
    }
    if (f.options === "devices" && props.model.stationId)
      list = list.filter((x) => x.stationId === props.model.stationId);
    return list.map((x) => ({
      label: `${x.name}${x.code && x.kind !== "people" ? " · " + x.code : ""}${f.options === "orders" ? " · " + (x.type || "") + " · " + (x.createdAt || "") + " · " + ((props.data.people || []).find((p) => p.id === x.assigneeId)?.name || "待分配") : ""}`,
      value: x.id,
    }));
  }
  if (f.key === "secondType") {
    let count =
      props.model.firstType === "PT操作票"
        ? 2
        : props.model.firstType === "集电线路操作票"
          ? 8
          : 6;
    return (f.options || [])
      .slice(0, count)
      .map((x) => ({ label: x, value: x }));
  }
  return Array.isArray(f.options)
    ? f.options.map((x) => ({ label: x, value: x }))
    : Object.entries(f.options || {}).map(([value, label]) => ({
        label,
        value,
      }));
}
</script>
<template>
  <div class="form-grid">
    <el-form-item
      v-for="f in fields"
      :key="f.key"
      :label="f.label"
      :class="{ wide: f.type === 'textarea' || f.type === 'multiRef' }"
      v-show="f.type !== 'source' || model.sourceType !== 'manual'"
    >
      <el-select
        v-if="['select', 'ref', 'multiRef', 'source'].includes(f.type)"
        v-model="model[f.key]"
        :multiple="f.type === 'multiRef'"
        filterable
        clearable
        :disabled="
          f.key === 'operatorId' ||
          (kind === 'defects' &&
            model.id &&
            ['sourceType', 'sourceId'].includes(f.key))
        "
        placeholder="请选择"
        ><el-option
          v-for="o in opts(f)"
          :key="o.value"
          :value="o.value"
          :label="o.label"
      /></el-select>
      <el-date-picker
        v-else-if="f.type === 'datetime' || f.type === 'date'"
        v-model="model[f.key]"
        :type="f.type === 'datetime' ? 'datetime' : 'date'"
        :value-format="
          f.type === 'datetime' ? 'YYYY-MM-DDTHH:mm:ss' : 'YYYY-MM-DD'
        "
        :placeholder="f.type === 'datetime' ? '选择日期和时间' : '选择日期'"
      />
      <el-input-number
        v-else-if="f.type === 'number'"
        v-model="model[f.key]"
        :min="0"
        :max="1000000"
        :precision="f.key === 'completionDays' ? 0 : 2"
        controls-position="right"
      />
      <el-input
        v-else
        v-model="model[f.key]"
        :type="f.type === 'textarea' ? 'textarea' : 'text'"
        :rows="f.type === 'textarea' ? 3 : undefined"
        :placeholder="'请填写' + f.label"
      />
    </el-form-item>
  </div>
</template>
