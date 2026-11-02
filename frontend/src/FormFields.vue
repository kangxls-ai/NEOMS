<script setup>
import { computed } from "vue";
const props = defineProps({
  model: Object,
  fields: Array,
  data: Object,
  user: Object,
});
function opts(f) {
  if (f.type === "source") {
    let a = ["warning", "alarm"].includes(props.model.sourceType)
      ? props.data.alerts
      : props.data.orders;
    return (a || []).map((x) => ({
      label: `${x.code} · ${x.name}`,
      value: x.id,
    }));
  }
  if (["ref", "multiRef"].includes(f.type))
    return (props.data[f.options] || []).map((x) => ({
      label: `${x.name}${x.code && x.kind !== "people" ? " · " + x.code : ""}`,
      value: x.id,
    }));
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
        :disabled="f.key === 'operatorId'"
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
