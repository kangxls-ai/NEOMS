<script setup>
import { computed } from "vue";
import FormFields from "./FormFields.vue";
const props = defineProps({
  action: String,
  model: Object,
  ticket: Object,
  data: Object,
  user: Object,
});
const f = (key, label, type = "text", options) => ({
  key,
  label,
  type,
  options,
});
const person = (key, label) => f(key, label, "ref", "people");
const processFields = computed(() => {
  let p = props.model.processType,
    base = [f("eventAt", "实际发生时间", "datetime")];
  if (p === "工作负责人变更")
    base.push(
      person("oldLeaderId", "原工作负责人"),
      person("newLeaderId", "新工作负责人"),
      person("approvedBy", "批准人"),
      f("approvalAt", "批准时间", "datetime"),
    );
  if (p === "工作人员变更")
    base.push(
      f("oldMemberIds", "原工作班成员", "multiRef", "people"),
      f("newMemberIds", "新工作班成员", "multiRef", "people"),
      person("approvedBy", "批准人"),
      f("approvalAt", "批准时间", "datetime"),
    );
  if (p === "工作延期")
    base.push(
      f("validUntil", "延期有效期", "datetime"),
      person("approvedBy", "批准人"),
      f("approvalAt", "批准时间", "datetime"),
    );
  if (p === "试运行申请")
    base.push(
      f("trialContent", "试运行范围与条件", "textarea"),
      person("approvedBy", "申请 / 确认人"),
    );
  if (p.includes("恢复"))
    base.push(
      f("restorationState", "恢复范围与措施状态", "textarea"),
      person("approvedBy", "恢复确认人"),
    );
  if (props.ticket.subtype === "wind" && p.includes("措施"))
    base.push(
      f("tagLockNumbers", "挂牌 / 上锁编号"),
      f("isolationState", "机械 / 电气隔离状态", "textarea"),
    );
  return base;
});
</script>
<template>
  <template v-if="action === 'process'"
    ><FormFields :fields="processFields" :model="model" :data="data" /><el-alert
      title="按已确认规则保存过程登记及批准信息，不新增审批链，也不覆盖原始计划与人员快照。"
      :closable="false"
      type="info" /></template
  ><template v-if="action === 'terminate'"
    ><FormFields
      :fields="[
        f(
          'workEndAt',
          ticket.kind === 'operationTickets'
            ? '操作执行结束时间'
            : '工作结束时间',
          'datetime',
        ),
        f('ticketEndAt', '票据终结时间', 'datetime'),
        ...(ticket.kind === 'operationTickets'
          ? [person('guardianId', '终结监护人')]
          : []),
        ...(['electrical1', 'wind'].includes(ticket.subtype)
          ? [
              f('removedGroundLines', '拆除接地线组数', 'number'),
              f('openedGroundSwitches', '拉开接地刀闸数', 'number'),
            ]
          : []),
      ]"
      :model="model"
      :data="data"
    /><template v-if="ticket.kind === 'workTickets'"
      ><el-checkbox v-model="model.siteRestored"
        >现场已恢复、清理完毕</el-checkbox
      ><el-checkbox v-model="model.personnelWithdrawn"
        >工作班人员已撤离</el-checkbox
      ></template
    ><el-checkbox v-else v-model="model.riskAcknowledged"
      >已知悉危险点并确认预控及顺序项目执行完毕</el-checkbox
    ></template
  >
</template>
