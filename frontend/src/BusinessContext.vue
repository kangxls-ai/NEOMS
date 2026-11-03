<script setup>
import { computed } from "vue";
import { statusNames, typeNames } from "./schema";
const props = defineProps({ record: Object, data: Object });
const emit = defineEmits(["view"]);
const names = (id) =>
  Object.values(props.data)
    .filter(Array.isArray)
    .flat()
    .find((x) => x.id === id)?.name ||
  id ||
  "—";
const fault = computed(() =>
  props.record.kind === "defects"
    ? props.record
    : props.record.defectSnapshot ||
      (props.data.defects || []).find((x) => x.id === props.record.defectId),
);
const order = computed(() =>
  (props.data.orders || []).find((x) => x.id === props.record.orderId),
);
const rule = computed(() => props.record.ruleSnapshot);
const device = computed(() =>
  props.record.kind === "devices"
    ? props.record
    : props.record.kind === "alerts"
      ? (props.data.devices || []).find((x) => x.id === props.record.deviceId)
      : null,
);
</script>
<template>
  <template v-if="device"
    ><div class="section-title">设备测点与最近采样（业务数据）</div>
    <p>
      {{ device.name }} · 温度阈值 {{ device.limit }}℃ · 最新采样
      {{ device.sampledAt || "尚无" }}
    </p>
    <el-table :data="[...(device.samples || [])].reverse()" max-height="320"
      ><el-table-column prop="time" label="采样时间" /><el-table-column
        prop="temperature"
        label="温度 ℃" /><el-table-column
        prop="power"
        label="有功功率 kW" /></el-table></template
  ><template v-if="fault"
    ><div class="section-title">
      {{
        record.kind === "defects" ? "缺陷与消缺进度" : "任务生成时的缺陷信息"
      }}
    </div>
    <el-descriptions :column="2" border
      ><el-descriptions-item label="来源">{{
        typeNames[fault.sourceType] || fault.sourceType
      }}</el-descriptions-item
      ><el-descriptions-item label="来源单据"
        ><el-button
          v-if="fault.sourceId"
          link
          type="primary"
          @click="emit('view', fault.sourceId)"
          >{{ names(fault.sourceId) }}</el-button
        ><span v-else>人工报障</span></el-descriptions-item
      ><el-descriptions-item label="设备 / 编码"
        >{{ names(fault.deviceId) }} /
        {{
          data.devices?.find((x) => x.id === fault.deviceId)?.deviceCode
        }}</el-descriptions-item
      ><el-descriptions-item label="缺陷类别">{{
        fault.level
      }}</el-descriptions-item
      ><el-descriptions-item label="缺陷描述" :span="2">{{
        fault.description
      }}</el-descriptions-item></el-descriptions
    ><template v-if="order"
      ><div class="linked-progress" @click="emit('view', order.id)">
        <b>{{ order.code }} · {{ statusNames[order.status] }}</b>
        <p>
          消缺负责人 {{ names(order.assigneeId) }} · 提交
          {{ order.submittedAt || "尚未提交" }} · 验收关闭
          {{ order.approvedAt || "尚未验收" }}
        </p>
        <el-button link type="primary">查看消缺工单及处置证据 →</el-button>
      </div></template
    >
    <div class="evidence-grid" v-if="fault.evidence?.length">
      <div v-for="(e, i) in fault.evidence" :key="i">
        <a v-if="e.url" :href="e.url" target="_blank"
          ><img
            v-if="/\.(png|jpe?g|webp)$/i.test(e.name || '')"
            :src="e.url"
            class="evidence-thumb"
          />{{ e.name }}</a
        ><span v-else>{{ e.name }}（模拟记录）</span>
      </div>
    </div></template
  ><template v-if="rule"
    ><div class="section-title">生成时规则快照（后续改规则不影响本工单）</div>
    <el-descriptions :column="2" border
      ><el-descriptions-item label="规则名称">{{
        rule.name
      }}</el-descriptions-item
      ><el-descriptions-item label="工作类型">{{
        rule.workType || rule.inspectionType
      }}</el-descriptions-item
      ><el-descriptions-item label="原计划内容" :span="2">{{
        rule.description
      }}</el-descriptions-item
      ><el-descriptions-item label="计划完成天数">{{
        rule.completionDays
      }}</el-descriptions-item
      ><el-descriptions-item label="触发时点">{{
        rule.times?.join("、")
      }}</el-descriptions-item></el-descriptions
    >
    <div v-for="p in rule.points || []" :key="p.id" class="point-summary">
      <b>{{ p.name }}</b>
      <p>设备 {{ (p.deviceIds || [p.deviceId]).map(names).join("、") }}</p>
      <p>项目 {{ p.items.map((x) => x.name).join("；") }}</p>
    </div></template
  >
</template>
