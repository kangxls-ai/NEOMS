<script setup>
const rows = [
  [
    "定检工作",
    "按计划周期开展的试验、切换、维护等工作；规则命中后生成定检工单。",
  ],
  ["巡检工作", "沿巡检线路逐点检查设备并记录/观察；发现异常可登记缺陷。"],
  [
    "异常事件",
    "模拟测点触发的预警或告警，需人工研判，不能直接等同于已确认缺陷。",
  ],
  [
    "缺陷",
    "经登记和审批跟踪的设备问题；审批通过产生消缺任务，验收通过才标为已消缺。",
  ],
  ["工单", "明确责任、时间、执行内容与结果的任务，分定检、巡检、消缺三类。"],
  ["工作票签发人", "按场站年度职责名单确定的工作票签发职责人员。"],
  ["工作负责人", "票上负责组织本次工作的人员；三类票承担签发后的第一次确认。"],
  ["工作许可人", "按场站年度职责名单确定，承担三类工作票的第二次确认。"],
  ["站长 / 值长", "承担场站与班次运行管理职责；本演示承担消缺独立验收。"],
  [
    "班次负责人",
    "当期班次的责任人；定检/巡检自动派发给该人，缺陷审批建议处理人也取当前班次。",
  ],
];
</script>
<template>
  <section class="panel">
    <div class="panel-title"><b>业务术语</b><span>统一产品用语</span></div>
    <el-table :data="rows" border
      ><el-table-column label="名称" width="180"
        ><template #default="{ row }">{{ row[0] }}</template></el-table-column
      ><el-table-column label="含义"
        ><template #default="{ row }">{{ row[1] }}</template></el-table-column
      ></el-table
    >
  </section>
  <section class="panel">
    <div class="panel-title">
      <b>人员职责关系</b><span>业务组织关系，不等同于本版账号权限</span>
    </div>
    <div class="responsibility-tree">
      <div>站长</div>
      <span>1 : N ↓</span>
      <div>值长</div>
      <span>1 : N ↓</span>
      <div>班次负责人</div>
      <span>组织本班值班人员，承接定时任务与缺陷审核</span>
    </div>
  </section>
</template>
