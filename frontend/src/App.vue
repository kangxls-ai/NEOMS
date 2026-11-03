<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Grid,
  Monitor,
  Document,
  Calendar,
  Setting,
  Bell,
  ArrowDown,
  Search,
  Refresh,
  Plus,
  SwitchButton,
  Clock,
  OfficeBuilding,
  Tools,
  Files,
  Warning,
} from "@element-plus/icons-vue";
import {
  menus,
  kinds,
  aliases,
  statusNames,
  typeNames,
  labels,
  fields,
  actionFields,
  actionNames,
} from "./schema";
import FormFields from "./FormFields.vue";
import CurrentLog from "./CurrentLog.vue";
import RecordEditor from "./RecordEditor.vue";
import BusinessList from "./BusinessList.vue";
import { handlers } from "./workflow";
import TicketExtra from "./TicketExtra.vue";
import HistoryPanel from "./HistoryPanel.vue";
import BusinessContext from "./BusinessContext.vue";
import Glossary from "./Glossary.vue";
import { operationTemplate } from "./operationTemplates";
const user = ref(null),
  users = ref([]),
  data = ref({}),
  loading = ref(false),
  page = ref("dashboard"),
  recordId = ref(""),
  search = ref(""),
  station = ref(""),
  filterStatus = ref(""),
  filterType = ref(""),
  detailTab = ref("overview"),
  history = ref([]),
  login = ref({ username: "admin", password: "Demo@2026" }),
  editing = ref(false),
  editKind = ref(""),
  draft = ref({}),
  actionDialog = ref(false),
  actionKey = ref(""),
  actionModel = ref({}),
  actionUploads = ref([]),
  previewDialog = ref(false),
  previewRows = ref([]),
  clockDialog = ref(false),
  clockTime = ref(""),
  rosterDialog = ref(false),
  rosterMode = ref("bulk"),
  rosterModel = ref({}),
  rosterPreview = ref([]),
  calendarDate = ref(new Date(2026, 10, 2)),
  selectedDate = ref("2026-11-02"),
  pointFilter = ref(""),
  importDialog = ref(false),
  importText = ref(""),
  importPreview = ref([]),
  messagesDialog = ref(false);
const businessDomain = ref("集中式场站运维");
const clone = (x) => JSON.parse(JSON.stringify(x));
async function api(url, method = "GET", body) {
  const opts = { method, credentials: "same-origin" };
  if (body instanceof FormData) opts.body = body;
  else if (body !== undefined) {
    opts.headers = { "Content-Type": "application/json" };
    opts.body = JSON.stringify(body);
  }
  const r = await fetch("/api" + url, opts);
  if (!r.ok) {
    let err;
    try {
      err = await r.json();
    } catch {
      err = { message: "请求失败，请检查服务日志" };
    }
    if (r.status === 401) user.value = null;
    throw Error(err.message || "请求失败");
  }
  return r.json();
}
async function safe(fn) {
  loading.value = true;
  try {
    return await fn();
  } catch (e) {
    if (e !== "cancel" && e !== "close")
      ElMessage.error(e.message || "操作未完成");
  } finally {
    loading.value = false;
  }
}
async function refresh() {
  data.value = await api("/bootstrap");
  clockTime.value = data.value.now;
}
function nav(p, id = "") {
  page.value = p;
  recordId.value = id;
  search.value = "";
  filterStatus.value = "";
  filterType.value = "";
  detailTab.value = "overview";
  history.value = [];
  pointFilter.value = "";
  location.hash = "/" + p + (id ? "/" + id : "");
  if (id) loadHistory(id);
}
function gotoRecord(id) {
  const r = all.value.find((x) => x.id === id);
  if (r)
    nav(
      r.kind === "shifts"
        ? "logs"
        : r.kind === "devices" && root.value === "运行管理"
          ? "operationsDevices"
          : r.kind,
      id,
    );
  else ElMessage.info("关联记录已归档或不存在");
}
async function loadHistory(id) {
  try {
    history.value = await api("/records/" + id + "/history");
  } catch (e) {
    ElMessage.error(e.message);
  }
}
async function signIn() {
  await safe(async () => {
    user.value = await api("/auth/login", "POST", login.value);
    await refresh();
    const parts = location.hash.replace(/^#\/?/, "").split("/");
    nav(parts[0] || "dashboard", parts[1] || "");
  });
}
async function switchUser(id) {
  await safe(async () => {
    user.value = await api("/auth/switch", "POST", { userId: id });
    await refresh();
    ElMessage.success("已切换业务身份：" + user.value.name);
  });
}
async function logout() {
  await api("/auth/logout", "POST");
  user.value = null;
}
const all = computed(() =>
    Object.values(data.value).filter(Array.isArray).flat(),
  ),
  current = computed(() => all.value.find((x) => x.id === recordId.value)),
  kind = computed(() => aliases[page.value] || page.value),
  title = computed(
    () =>
      ({
        dashboard: "工作台",
        monitor: "设备监测",
        operationsMonitor: "设备监测",
        operationsDevices: "设备信息",
        currentLog: "当前日志",
        logs: "日志查询",
        calendar: "值班日历",
        workflow: "固定审批流程",
        guide: "业务指引",
        defectApproval: "缺陷审批",
        unassigned: "待分配工单",
        paused: "已挂起工单",
        acceptance: "消缺验收",
        workApproval: "工作票待办",
        operationApproval: "操作票待办",
      })[page.value] ||
      kinds[page.value] ||
      page.value,
  );
const root = computed(() =>
  page.value === "dashboard"
    ? "工作台"
    : ["stations", "devices", "monitor"].includes(page.value)
      ? "场站信息管理"
      : [
            "workHazards",
            "operationHazards",
            "duties",
            "workflow",
            "guide",
            "people",
            "teams",
          ].includes(page.value)
        ? "系统管理"
        : "运行管理",
);
const sidebar = computed(() =>
  root.value === "系统管理"
    ? menus
        .filter((x) => x.title === "基础配置")
        .concat([
          {
            title: "基础资料",
            items: [
              ["people", "业务人员"],
              ["teams", "运维班组"],
            ],
          },
        ])
    : root.value === "场站信息管理"
      ? [
          {
            title: "场站信息",
            items: [
              ["stations", "场站资料"],
              ["devices", "设备台账"],
              ["monitor", "设备监测"],
            ],
          },
        ]
      : root.value === "工作台"
        ? [
            {
              title: "工作台",
              items: [
                ["dashboard", "业务总览"],
                ["messages", "系统消息"],
                ["currentLog", "当前日志"],
              ],
            },
          ]
        : menus.filter((x) => x.title !== "基础配置"),
);
const rows = computed(() => {
  let a = (data.value[kind.value] || []).filter((x) => x.status !== "removed");
  if (page.value === "defectApproval")
    a = a.filter((x) => ["pendingApproval", "returned"].includes(x.status));
  if (page.value === "unassigned")
    a = a.filter((x) => x.status === "unassigned");
  if (page.value === "paused") a = a.filter((x) => x.status === "paused");
  if (page.value === "acceptance")
    a = a.filter(
      (x) => x.type === "corrective" && ["review", "closed"].includes(x.status),
    );
  if (page.value === "workApproval")
    a = a.filter((x) =>
      ["pendingSign", "pendingConfirm", "pendingPermit", "returned"].includes(
        x.status,
      ),
    );
  if (page.value === "operationApproval")
    a = a.filter((x) => ["pendingSign", "returned"].includes(x.status));
  return a.filter(
    (x) =>
      (!station.value || x.stationId === station.value) &&
      (!filterStatus.value || x.status === filterStatus.value) &&
      (!filterType.value ||
        x.type === filterType.value ||
        x.subtype === filterType.value) &&
      (!search.value ||
        JSON.stringify([
          x.name,
          x.code,
          x.description,
          labelId(x.assigneeId),
          labelId(x.deviceId),
        ])
          .toLowerCase()
          .includes(search.value.toLowerCase())),
  );
});
function labelId(id) {
  if (!id) return "—";
  return all.value.find((x) => x.id === id)?.name || id;
}
const recommendation = computed(() => {
  let r = current.value || {};
  let device = (data.value.devices || []).find((x) => x.id === r.deviceId);
  let teams = (data.value.teams || []).filter(
    (x) => x.stationId === r.stationId,
  );
  let team =
    teams.find((x) => (x.skill || "").includes(device?.type)) || teams[0];
  let leader = team?.leaderId || "person-field";
  let load = (data.value.orders || []).filter(
    (x) =>
      x.assigneeId === leader && !["closed", "completed"].includes(x.status),
  ).length;
  return {
    teamId: team?.id || "",
    assigneeId: leader,
    text: `推荐${team?.name || "场站负责人"} / ${labelId(leader)}：同场站，${device?.type || "设备"}技能匹配优先，当前在办${load}单。调度可调整后确认。`,
  };
});
function fmt(v) {
  return v ? String(v).replace("T", " ").slice(0, 19) : "—";
}
function st(s) {
  return statusNames[s] || s || "—";
}
function typ(s) {
  return typeNames[s] || s || "—";
}
function tagType(s) {
  return [
    "closed",
    "completed",
    "terminated",
    "repaired",
    "ended",
    "active",
    "handedOver",
  ].includes(s)
    ? "success"
    : ["returned", "void"].includes(s)
      ? "danger"
      : [
            "pending",
            "pendingApproval",
            "pendingSign",
            "pendingConfirm",
            "pendingPermit",
            "unassigned",
            "paused",
            "handoverPending",
            "review",
          ].includes(s)
        ? "warning"
        : "info";
}
function overdue(r) {
  return (
    r.dueAt &&
    !["closed", "completed"].includes(r.status) &&
    r.dueAt < data.value.now
  );
}
const counts = computed(() => ({
  alerts: (data.value.alerts || []).filter((x) => x.status === "pending")
    .length,
  defects: (data.value.defects || []).filter(
    (x) => x.status === "pendingApproval",
  ).length,
  dispatch: (data.value.orders || []).filter((x) =>
    ["pendingDispatch", "unassigned"].includes(x.status),
  ).length,
  review: (data.value.orders || []).filter((x) => x.status === "review").length,
  closed: (data.value.orders || []).filter((x) =>
    ["closed", "completed"].includes(x.status),
  ).length,
  overdue: (data.value.orders || []).filter(overdue).length,
}));
const allTodos = computed(() => [
  ...(data.value.alerts || []).filter((x) => x.status === "pending"),
  ...(data.value.defects || []).filter((x) => x.status === "pendingApproval"),
  ...(data.value.orders || []).filter(
    (x) => !["closed", "completed"].includes(x.status),
  ),
  ...(data.value.workTickets || []).filter((x) =>
    ["pendingSign", "pendingConfirm", "pendingPermit"].includes(x.status),
  ),
]);
const focusMine = ref(true);
const todos = computed(() =>
  allTodos.value
    .filter(
      (x) =>
        !focusMine.value ||
        user.value?.role === "管理员" ||
        handlers(x, data.value).ids.includes(user.value?.id) ||
        (x.kind === "alerts" && user.value?.role === "调度"),
    )
    .slice(0, 12),
);
const blockedTickets = computed(
  () =>
    current.value?.ticketIds
      ?.map((id) => all.value.find((x) => x.id === id))
      .filter((x) => x && x.status !== "terminated") || [],
);
const linked = computed(() => {
  let r = current.value;
  if (!r) return [];
  let ids = [
    r.sourceId,
    r.defectId,
    r.orderId,
    r.ruleId,
    ...(r.ticketIds || []),
    ...(r.orderIds || []),
    ...(r.commandIds || []),
    ...(r.noticeIds || []),
  ].filter(Boolean);
  return all.value.filter(
    (x) =>
      ids.includes(x.id) ||
      x.orderId === r.id ||
      x.sourceId === r.id ||
      x.defectId === r.id,
  );
});
const steps = computed(() => {
  let r = current.value;
  if (!r) return [];
  if (r.kind === "orders")
    return r.type === "corrective"
      ? ["待派发", "待接单", "待开工", "执行中", "待验收", "已关闭"]
      : ["待分配", "待开工", "执行中", "已完成"];
  if (r.kind === "workTickets")
    return r.subtype === "contact"
      ? ["草稿", "待签发", "执行中 / 待终结", "已终结"]
      : [
          "草稿",
          "待签发",
          "待负责人确认",
          "待许可人许可",
          "执行中 / 待终结",
          "已终结",
        ];
  if (r.kind === "operationTickets")
    return ["草稿", "待签发", "执行中 / 待终结", "已终结"];
  if (r.kind === "defects") return ["待审批", "待消缺", "已消缺"];
  return [];
});
const stageIndex = computed(() =>
  Math.max(0, steps.value.indexOf(st(current.value?.status))),
);
const detailFields = computed(() => {
  let r = current.value || {};
  let keys = [
    "name",
    "code",
    "stationId",
    "deviceId",
    "deviceName",
    "type",
    "subtype",
    "status",
    "description",
    "assigneeId",
    "teamId",
    "defectId",
    "ruleId",
    "requiresPermit",
    "recoveryLimit",
    "revision",
    "leaderId",
    "signedBy",
    "confirmedBy",
    "permittedBy",
    "terminatedBy",
    ...(fields[r.kind] || []).map((x) => x.key),
    "approvedStart",
    "approvedEnd",
    "signedNote",
    "confirmedNote",
    "permittedNote",
    "executionEndedAt",
    "finishedAt",
    "sampledAt",
    "plannedAt",
    "dueAt",
    "acceptedAt",
    "startedAt",
    "submittedAt",
    "approvedAt",
    "signedAt",
    "confirmedAt",
    "permittedAt",
    "terminatedAt",
    "count",
    "firstAt",
    "lastAt",
    "temperature",
    "power",
    "reason",
    "returnReason",
    "pauseReason",
    "conclusion",
    "handover",
    "handoverAt",
    "createdAt",
  ];
  return [...new Set(keys)].filter(
    (k) =>
      r[k] !== undefined &&
      r[k] !== "" &&
      !Array.isArray(r[k]) &&
      typeof r[k] !== "object",
  );
});
function val(r, k) {
  let v = r[k];
  if (k === "status") return st(v);
  if (k.endsWith("By")) return labelId(v);
  if (typeof v === "boolean") return v ? "是" : "否";
  if (k.endsWith("Id")) return labelId(v);
  if (k === "sourceType" || k === "subtype" || k === "type") return typ(v);
  return /^\d{4}-\d{2}-\d{2}T/.test(String(v)) ? fmt(v) : String(v);
}
function fieldLabel(k, r = current.value) {
  return (
    (fields[r?.kind] || []).find((f) => f.key === k)?.label || labels[k] || k
  );
}
function actions(r) {
  if (!r) return [];
  let s = r.status;
  if (r.kind === "alerts") return s === "pending" ? ["confirm", "exclude"] : [];
  if (r.kind === "devices") return ["sample"];
  if (r.kind === "defects")
    return s === "pendingApproval"
      ? ["approve", "return", "void"]
      : s === "returned"
        ? ["resubmit", "void"]
        : [];
  if (r.kind === "orders") {
    if (["pendingDispatch", "unassigned"].includes(s)) return ["dispatch"];
    if (s === "pendingAcceptance") return ["accept", "reassign"];
    if (s === "ready") return ["start", "reassign", "material"];
    if (s === "working")
      return r.type === "corrective"
        ? ["submit", "pause", "transfer", "extend", "material"]
        : ["finish", "pause", "transfer", "extend", "material"];
    if (s === "paused") return ["resume", "transfer", "extend", "material"];
    if (s === "review") return ["approve", "reject"];
    return [];
  }
  if (r.kind?.endsWith("Tickets")) {
    if (["draft", "returned"].includes(s)) return ["submit", "void"];
    if (["pendingSign", "pendingConfirm", "pendingPermit"].includes(s))
      return ["approve", "return", "void"];
    if (s === "executing") return ["process", "terminate"];
    return [];
  }
  if (r.kind?.endsWith("Rules"))
    return ["toggle", ...(s === "active" ? ["trigger"] : [])];
  if (r.kind === "commands")
    return s === "pendingOperation"
      ? ["operate"]
      : s === "pendingReply"
        ? ["reply"]
        : [];
  if (r.kind === "shifts" && r.endAt <= data.value.now) return ["handover"];
  return [];
}
function actionLabel(a, r = current.value) {
  if (a === "submit" && r?.kind?.endsWith("Tickets")) return "提交签发";
  if (a === "approve") {
    if (r?.kind === "orders") return "验收通过并关闭";
    if (r?.status === "pendingConfirm") return "工作负责人确认";
    if (r?.status === "pendingPermit") return "工作许可人许可";
    if (r?.kind?.endsWith("Tickets")) return "签发通过";
    return "审批通过并生成消缺工单";
  }
  return actionNames[a] || a;
}
function canEdit(r) {
  return (
    fields[r.kind] &&
    !["orders", "alerts", "people", "notes"].includes(r.kind) &&
    (!["defects", "workTickets", "operationTickets"].includes(r.kind) ||
      ["draft", "returned"].includes(r.status))
  );
}
function addHours(str, h) {
  const d = new Date(str);
  d.setHours(d.getHours() + h);
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}
function openEditor(k, r, extra = {}) {
  editKind.value = k;
  let base = { name: "", stationId: station.value || "station-1" };
  if (k === "defects")
    Object.assign(base, {
      level: "一般缺陷",
      sourceType: "manual",
      evidence: [],
    });
  if (k.endsWith("Rules"))
    Object.assign(base, {
      completionDays: 1,
      months: [],
      weeks: [],
      weekdays: [],
      days: [],
      times: ["10:00"],
      ...(k === "inspectionRules"
        ? { points: [], inspectionType: "场内巡检" }
        : { deviceIds: [], workType: "定期维护" }),
    });
  if (k.endsWith("Tickets"))
    Object.assign(base, {
      memberIds: [],
      orderIds: [],
      commandIds: [],
      noticeIds: [],
      hazards: [],
      leaderId: "person-field",
      plannedStart: data.value.now,
      plannedEnd: addHours(data.value.now, 4),
      ...(k === "workTickets"
        ? { subtype: "electrical1", measures: [] }
        : {
            operatorId: user.value.id,
            issuerId: currentShift.value?.assigneeId || user.value.id,
            supervisorId: "person-permit",
            firstType: "集电线路操作票",
            secondType: "运行转检修",
            operationType: "监护下操作",
            items: [],
          }),
    });
  if (k === "duties") base.year = data.value.now.slice(0, 4);
  if (k === "shifts")
    Object.assign(base, {
      memberIds: [],
      startAt: addHours(data.value.now, 1),
      endAt: addHours(data.value.now, 13),
      assigneeId: "person-field",
      chiefId: "person-chief",
    });
  if (k === "commands" || k === "notices") base.issuerId = user.value.id;
  draft.value = r ? clone(r) : { ...base, ...extra };
  for (const f of fields[k] || [])
    if (f.type === "number" && draft.value[f.key] != null)
      draft.value[f.key] = Number(draft.value[f.key]);
  editing.value = true;
}
function fillTicket() {
  let d = draft.value;
  let order = all.value.find((x) => x.id === d.orderIds?.[0]);
  let device = all.value.find((x) => x.id === order?.deviceId);
  let op = editKind.value === "operationTickets";
  Object.assign(d, {
    name: (op ? "操作票" : typ(d.subtype)) + " · 逆变器异常检修",
    deviceName: device?.name || "02号逆变器",
    description: "作业：检查散热风扇及风道，记录设备状态与恢复结果。",
    leaderId: "person-field",
    issuerId: "person-signer",
    permitterId: "person-permit",
    supervisorId: "person-permit",
    memberIds: ["person-field", "person-permit"],
    firstType: d.firstType || "集电线路操作票",
    secondType: d.secondType || "运行转检修",
    operationType: "监护下操作",
    hazards: clone(
      (op ? data.value.operationHazards : data.value.workHazards).slice(0, 1),
    ),
  });
  if (op) d.items = operationTemplate(d.firstType, d.secondType, d.deviceName);
  else
    d.measures =
      d.subtype === "contact"
        ? []
        : [
            {
              id: crypto.randomUUID(),
              group:
                d.subtype === "electrical2"
                  ? "工作条件及保留带电设备"
                  : d.subtype === "wind"
                    ? "运行人员安全措施"
                    : "应拉断路器、隔离开关",
              name: "本次作业隔离范围与设备状态核查（措施）",
              executed: false,
            },
          ];
}
async function saveEditor() {
  await safe(async () => {
    const r = draft.value.id
      ? await api("/records/" + draft.value.id, "PUT", draft.value)
      : await api("/entities/" + editKind.value, "POST", draft.value);
    editing.value = false;
    await refresh();
    nav(r.kind, r.id);
    ElMessage.success("已保存至数据库");
  });
}
async function openAction(a, r = current.value, extra = {}) {
  if (r && r.id !== recordId.value) {
    recordId.value = r.id;
  }
  actionKey.value = a;
  actionModel.value = {
    reason: "",
    description: "",
    assigneeId: r?.assigneeId || "person-field",
    teamId: r?.teamId || "team-1",
    dueAt:
      r?.dueAt && r.dueAt > data.value.now
        ? r.dueAt
        : addHours(data.value.now, 4),
    quantity: 1,
    price: 0,
    date: data.value.now?.slice(0, 10),
    temperature: 83,
    level: "warning",
    defectLevel: "一般缺陷",
    processType: "工作负责人变更",
    eventAt: data.value.now,
    oldLeaderId: r?.leaderId,
    oldMemberIds: clone(r?.memberIds || []),
    newMemberIds: clone(r?.memberIds || []),
    approvalAt: data.value.now,
    approvedBy: user.value.id,
    workEndAt: data.value.now,
    ticketEndAt: data.value.now,
    guardianId: r?.supervisorId || user.value.id,
    removedGroundLines: 0,
    openedGroundSwitches: 0,
    approvedStart: r?.plannedStart,
    approvedEnd: r?.plannedEnd,
    ...extra,
  };
  actionUploads.value = [];
  if (a === "dispatch" && r?.type === "corrective")
    Object.assign(actionModel.value, {
      teamId: recommendation.value.teamId,
      assigneeId: recommendation.value.assigneeId,
    });
  if (a === "approve" && r.kind === "orders") {
    actionModel.value = { conclusion: "", checked: false };
    actionDialog.value = true;
  } else if (
    a === "approve" &&
    r.kind.endsWith("Tickets") &&
    ["pendingSign", "pendingConfirm", "pendingPermit"].includes(r.status)
  ) {
    actionDialog.value = true;
  } else if (actionFields[a]) actionDialog.value = true;
  else
    await safe(async () => {
      await ElMessageBox.confirm(
        "确认" + actionLabel(a, r) + "？",
        "业务操作确认",
        { type: "info" },
      );
      await performAction(a, {});
    });
}
async function performAction(a, p) {
  if (a === "terminate") p.attachmentIds = actionUploads.value.map((x) => x.id);
  if (a === "confirm") p.evidence = clone(actionUploads.value);
  await api("/records/" + recordId.value + "/actions/" + a, "POST", p);
  actionDialog.value = false;
  await refresh();
  await loadHistory(recordId.value);
  ElMessage.success("操作成功，业务记录已保存");
}
async function commitAction() {
  await safe(() => performAction(actionKey.value, actionModel.value));
}
async function uploadFile(file, target) {
  await safe(async () => {
    let fd = new FormData();
    fd.append("file", file);
    let r = await api("/uploads", "POST", fd);
    target.push(r);
  });
  return false;
}
async function mockAttachment() {
  const f = new File(
    [
      "NEOMS 作业票据附件\n工作结束，现场恢复及人员撤离记录。\n" +
        data.value.now,
    ],
    "票据终结记录.txt",
    { type: "text/plain" },
  );
  await uploadFile(f, actionUploads.value);
}
function uploadDraft(f) {
  return uploadFile(f, (draft.value.evidence ||= []));
}
const workForm = ref({});
function initWork() {
  let r = current.value;
  if (r)
    workForm.value = {
      rootCause: r.rootCause || "",
      work: r.work || "",
      recoveryTemp: Number(r.recoveryTemp || 0),
      recoveryPower: Number(r.recoveryPower || 0),
      observation: Number(r.observation || 0),
      evidence: clone(r.evidence || []),
      remarks: r.remarks || "",
    };
}
async function saveWork() {
  await safe(async () => {
    await api(
      "/records/" + recordId.value + "/actions/record",
      "POST",
      workForm.value,
    );
    await refresh();
    await loadHistory(recordId.value);
    ElMessage.success("处置过程已保存");
  });
}
function fillWork() {
  workForm.value = {
    ...workForm.value,
    rootCause: "冷却风扇磨损及散热风道积尘",
    work: "按现场作业要求更换冷却风扇，清洁风道，复测设备恢复。",
    recoveryTemp: 54,
    recoveryPower: 905,
    observation: 30,
  };
}
async function saveItem(it) {
  await safe(async () => {
    await api("/records/" + recordId.value + "/actions/item", "POST", {
      itemId: it.id,
      result: String(it.result || ""),
      abnormal: !!it.abnormal,
    });
    await refresh();
    ElMessage.success("工作项结果已保存");
  });
}
async function executeStep(it) {
  await safe(async () => {
    await api("/records/" + recordId.value + "/actions/step", "POST", {
      itemId: it.id,
      result: "已执行",
    });
    await refresh();
    await loadHistory(recordId.value);
  });
}
async function executeHazard(it) {
  await safe(async () => {
    await api("/records/" + recordId.value + "/actions/hazard", "POST", {
      itemId: it.id,
    });
    await refresh();
    await loadHistory(recordId.value);
  });
}
function sparkline(d) {
  let samples = d.samples || [];
  if (!samples.length) return "";
  let values = samples.map((x) => Number(x.temperature)),
    max = Math.max(...values, Number(d.limit || 75)) + 5,
    min = Math.min(...values) - 5;
  return values
    .map(
      (v, i) =>
        `${i ? "L" : "M"}${samples.length > 1 ? (i / (samples.length - 1)) * 440 : 220} ${90 - ((v - min) / (max - min)) * 80}`,
    )
    .join(" ");
}
async function previewRule(r) {
  await safe(async () => {
    previewRows.value = await api("/rules/preview", "POST", r);
    previewDialog.value = true;
  });
}
const shiftRows = computed(() =>
    (data.value.shifts || [])
      .filter((x) => !station.value || x.stationId === station.value)
      .sort((a, b) => a.startAt.localeCompare(b.startAt)),
  ),
  dayShifts = computed(() =>
    shiftRows.value.filter(
      (x) =>
        x.startAt < selectedDate.value + "T23:59:59" &&
        x.endAt > selectedDate.value + "T00:00:00",
    ),
  ),
  currentShift = computed(() =>
    shiftRows.value.find(
      (x) => x.startAt <= data.value.now && x.endAt > data.value.now,
    ),
  );
window.addEventListener("hashchange", () => {
  if (!user.value) return;
  let [p, id = ""] = location.hash.replace(/^#\/?/, "").split("/");
  if (p && (p !== page.value || id !== recordId.value)) nav(p, id);
});
function openRoster(mode) {
  rosterMode.value = mode;
  rosterPreview.value = [];
  rosterModel.value =
    mode === "leave"
      ? { shiftId: "", endAt: "2026-11-07T23:59:00" }
      : {
          stationId: station.value || "station-1",
          startDate: "2026-11-08",
          endDate: "2026-11-10",
          chiefId: "person-chief",
          peopleIds: ["person-field", "person-signer", "person-permit"],
          slots: [
            { name: "早班", start: "08:00", end: "20:00", nextDay: false },
            { name: "夜班", start: "20:00", end: "08:00", nextDay: true },
          ],
        };
  rosterDialog.value = true;
}
async function rosterDo(save) {
  await safe(async () => {
    const path =
      rosterMode.value === "leave"
        ? save
          ? "/roster/leave"
          : "/roster/leave-preview"
        : save
          ? "/roster/apply"
          : "/roster/preview";
    rosterPreview.value = await api(path, "POST", rosterModel.value);
    if (save) {
      await refresh();
      rosterDialog.value = false;
      ElMessage.success("排班已保存");
    }
  });
}
async function advance(time) {
  await safe(async () => {
    const r = await api("/clock", "POST", { time });
    await refresh();
    clockDialog.value = false;
    ElMessage.success(`时间已推进，触发${r.triggered}次规则检查`);
  });
}
async function reset() {
  await safe(async () => {
    await ElMessageBox.confirm(
      "将重置本项目的记录，当前操作将清除。是否继续？",
      "重置数据",
      { type: "warning" },
    );
    await api("/demo/reset", "POST", { confirm: "RESET_DEMO" });
    await refresh();
    nav("dashboard");
    ElMessage.success("数据已恢复");
  });
}
function downloadCSV() {
  let text =
    "\uFEFF年份,场站,职责类型,姓名\n2026,芳草路光伏电站,工作负责人,李明\n";
  let url = URL.createObjectURL(
    new Blob([text], { type: "text/csv;charset=utf-8" }),
  );
  let a = document.createElement("a");
  a.href = url;
  a.download = "年度职责人员模板.csv";
  a.click();
  URL.revokeObjectURL(url);
}
function importRows() {
  return importText.value
    .replace(/^\uFEFF/, "")
    .trim()
    .split(/\r?\n/)
    .slice(1)
    .filter(Boolean)
    .map((l) => {
      let [year, st, dutyType, person] = l.split(",").map((x) => x.trim());
      return {
        year,
        stationId:
          (data.value.stations || []).find((x) => x.name === st || x.id === st)
            ?.id || st,
        dutyType,
        personId:
          (data.value.people || []).find(
            (x) => x.name === person || x.id === person,
          )?.id || person,
      };
    });
}
async function importDo(save) {
  await safe(async () => {
    importPreview.value = await api(
      save ? "/duties/import" : "/duties/import-preview",
      "POST",
      importRows(),
    );
    if (save) {
      await refresh();
      ElMessage.success("导入完成，已存在及错误行未覆盖");
    }
  });
}
async function deleteDuty(r) {
  await safe(async () => {
    await ElMessageBox.confirm(
      "删除该年度职责记录？保留操作历史。",
      "确认删除",
    );
    await api("/records/" + r.id, "DELETE");
    await refresh();
    recordId.value = "";
  });
}
async function readMessage(r) {
  await safe(async () => {
    await api("/records/" + r.id + "/actions/read", "POST", {});
    await refresh();
    messagesDialog.value = false;
    if (r.targetId) gotoRecord(r.targetId);
  });
}
function viewTab(v) {
  detailTab.value = v;
  if (v === "execution") initWork();
  if (v === "history") loadHistory(recordId.value);
}
function printTicket(draftMode) {
  window.open(
    "/api/print/" + recordId.value + "?draft=" + draftMode,
    "_blank",
    "noopener",
  );
}
onMounted(async () => {
  await safe(async () => {
    users.value = await api("/auth/users");
    try {
      user.value = await api("/auth/me");
      await refresh();
      let p = location.hash.replace(/^#\/?/, "").split("/");
      nav(p[0] || "dashboard", p[1] || "");
    } catch {
      user.value = null;
    }
  });
});
</script>

<template>
  <div v-if="!user" class="login-page">
    <div class="login-art">
      <div class="orbit one"></div>
      <div class="orbit two"></div>
      <div class="energy-building"><span v-for="n in 12" :key="n"></span></div>
      <div class="solar-array"></div>
    </div>
    <div class="login-card">
      <div class="brand-mark">◉</div>
      <h1>智慧运营中心</h1>
      <p class="subtle">NEOMS · 新能源场站运行管理</p>
      <el-tabs model-value="account"
        ><el-tab-pane label="账号登录" name="account"
          ><el-form label-position="top" @submit.prevent="signIn"
            ><el-form-item label="示例账号"
              ><el-select v-model="login.username"
                ><el-option
                  v-for="u in users"
                  :key="u.id"
                  :label="u.name + ' · ' + u.role + '（' + u.username + '）'"
                  :value="u.username" /></el-select></el-form-item
            ><el-form-item label="密码"
              ><el-input
                v-model="login.password"
                type="password"
                show-password
                @keyup.enter="signIn" /></el-form-item
            ><el-button
              type="primary"
              class="full"
              :loading="loading"
              @click="signIn"
              >登 录</el-button
            ></el-form
          ></el-tab-pane
        ></el-tabs
      >
      <div class="login-help">
        登录密码：Demo@2026<br />业务数据 · 业务身份切换 · 不含复杂权限隔离
      </div>
    </div>
    <div class="login-footer">智慧运营中心 · NEOMS 运行管理系统</div>
  </div>
  <div v-else class="app-shell" v-loading="loading">
    <header class="topbar">
      <div class="brand" @click="nav('dashboard')">
        <span>◉</span><b>智慧运营中心</b>
      </div>
      <nav>
        <button
          v-for="t in ['工作台', '场站信息管理', '运行管理', '系统管理']"
          :key="t"
          :class="{ active: root === t }"
          @click="
            nav(
              t === '工作台'
                ? 'dashboard'
                : t === '场站信息管理'
                  ? 'stations'
                  : t === '系统管理'
                    ? 'workHazards'
                    : 'orders',
            )
          "
        >
          <el-icon><Grid /></el-icon>{{ t }}
        </button>
      </nav>
      <div class="top-right">
        <el-button
          text
          @click="
            clockTime = data.now;
            clockDialog = true;
          "
          ><el-icon><Clock /></el-icon
          >{{ fmt(data.now).slice(5, 16) }}</el-button
        ><el-badge
          :value="(data.messages || []).filter((m) => !m.read).length"
          :hidden="!(data.messages || []).some((m) => !m.read)"
          ><el-button text @click="messagesDialog = true"
            ><el-icon><Bell /></el-icon></el-button></el-badge
        ><el-dropdown @command="switchUser"
          ><span class="user-switch"
            >{{ user.name }} · {{ user.role }}
            <el-icon><ArrowDown /></el-icon></span
          ><template #dropdown
            ><el-dropdown-menu
              ><el-dropdown-item v-for="u in users" :key="u.id" :command="u.id"
                >{{ u.name }} · {{ u.role }}</el-dropdown-item
              ></el-dropdown-menu
            ></template
          ></el-dropdown
        ><el-button text title="退出登录" @click="logout"
          ><el-icon><SwitchButton /></el-icon
        ></el-button>
      </div>
    </header>
    <aside class="sidebar">
      <div class="sidebar-caption">{{ root }}</div>
      <el-menu
        :default-active="
          page === 'operationsDevices' ? 'operationsMonitor' : page
        "
        :default-openeds="sidebar.map((x) => x.title)"
        @select="(p) => nav(p)"
        ><el-sub-menu
          v-for="group in sidebar"
          :key="group.title"
          :index="group.title"
          ><template #title
            ><el-icon><Files /></el-icon>{{ group.title }}</template
          ><el-menu-item v-for="m in group.items" :key="m[0]" :index="m[0]">{{
            m[1]
          }}</el-menu-item></el-sub-menu
        ></el-menu
      >
      <div class="sidebar-bottom"><i></i> 系统运行正常</div>
    </aside>
    <main class="main">
      <div class="breadcrumb">
        <el-select
          v-model="businessDomain"
          size="small"
          class="business-domain"
          style="width: 190px"
          ><el-option
            v-for="d in [
              '集中式场站运维',
              '分布式能源',
              '户用储能',
              '能碳管理',
              '全部',
            ]"
            :key="d"
            :value="d"
            :label="d"
        /></el-select>
        {{ root }} <span>/</span> {{ title }}
        <span v-if="current">/ {{ current.code }}</span>
      </div>
      <el-alert
        v-if="!['集中式场站运维', '全部'].includes(businessDomain)"
        :title="
          businessDomain +
          '本次仅保留业务域入口，以下仍为已定义的场站运行数据。'
        "
        type="info"
        :closable="false"
        class="mb"
      />
      <template v-if="page === 'dashboard'"
        ><section class="welcome">
          <div>
            <div class="eyebrow">NEOMS OPERATIONS</div>
            <h1>你好，{{ user.name }}</h1>
            <p>异常有依据，任务有责任，作业与验收全程可追溯。</p>
            <div class="welcome-tags">
              <span>当前业务身份：{{ user.role }}</span
              ><span>{{ (data.stations || []).length }} 座场站</span
              ><span>数据已保存至数据库</span>
            </div>
          </div>
          <div class="welcome-visual">
            <div class="pv-grid"></div>
            <span class="windmill">✦</span>
          </div>
        </section>
        <section class="panel">
          <div class="panel-title">
            <b>运行管理概览</b><span>点击指标进入业务处理</span>
          </div>
          <div class="metric-grid">
            <button
              v-for="m in [
                [counts.alerts, '待研判异常', 'alerts'],
                [counts.defects, '待审批缺陷', 'defectApproval'],
                [counts.dispatch, '待派发 / 待分配', 'orders'],
                [counts.review, '待验收消缺', 'acceptance'],
                [counts.closed, '已完成工单', 'orders'],
              ]"
              :key="m[1]"
              class="metric"
              @click="nav(m[2])"
            >
              <label>{{ m[1] }}</label
              ><strong>{{ m[0] }}<small>项</small></strong
              ><span>查看详情 →</span>
            </button>
          </div>
        </section>
        <div class="dashboard-grid">
          <section class="panel">
            <div class="panel-title">
              <b>业务待办</b
              ><el-switch
                v-model="focusMine"
                active-text="当前视角"
                inactive-text="全部"
              /><el-button link type="primary" @click="nav('orders')"
                >查看全部工单</el-button
              >
            </div>
            <el-table
              :data="todos"
              @row-click="(r) => gotoRecord(r.id)"
              class="click-table"
              ><el-table-column label="事项 / 编号" min-width="280"
                ><template #default="{ row }"
                  ><b>{{ row.name }}</b
                  ><small>{{ row.code }}</small></template
                ></el-table-column
              ><el-table-column label="类型" width="110"
                ><template #default="{ row }">{{
                  kinds[row.kind]
                }}</template></el-table-column
              ><el-table-column label="当前状态" width="150"
                ><template #default="{ row }"
                  ><el-tag :type="tagType(row.status)" effect="light">{{
                    st(row.status)
                  }}</el-tag></template
                ></el-table-column
              ><el-table-column label="责任人" width="110"
                ><template #default="{ row }">{{
                  labelId(row.assigneeId || row.leaderId)
                }}</template></el-table-column
              ><el-table-column label="操作" width="80"
                ><template #default="{ row }"
                  ><el-button
                    link
                    type="primary"
                    @click.stop="gotoRecord(row.id)"
                    >处理</el-button
                  ></template
                ></el-table-column
              ></el-table
            >
          </section>
          <div>
            <section class="panel attention">
              <div class="panel-title"><b>需要关注</b></div>
              <div class="attention-item" @click="nav('unassigned')">
                <el-tag type="warning">缺班补派</el-tag>
                <p>
                  {{
                    (data.orders || []).filter((x) => x.status === "unassigned")
                      .length
                  }}项任务待分配，原计划时间保留
                </p>
              </div>
              <div class="attention-item" @click="nav('orders')">
                <el-tag type="danger">超时关注</el-tag>
                <p>{{ counts.overdue }}项工单超时，挂起不自动顺延期限</p>
              </div>
              <div class="attention-item" @click="nav('currentLog')">
                <el-tag type="warning">交代待补</el-tag>
                <p>
                  {{
                    (data.shifts || []).filter(
                      (x) => x.status === "handoverPending",
                    ).length
                  }}班交代待补，不阻塞下一班
                </p>
              </div>
            </section>
            <section class="panel">
              <div class="panel-title"><b>当前班次</b></div>
              <template v-if="currentShift"
                ><h3>
                  {{ currentShift.name }} ·
                  {{ labelId(currentShift.assigneeId) }}
                </h3>
                <p>
                  {{ fmt(currentShift.startAt) }}<br />至
                  {{ fmt(currentShift.endAt) }}
                </p>
                <el-button type="primary" plain @click="nav('currentLog')"
                  >进入当前日志</el-button
                ></template
              ><el-empty v-else description="当前场站缺少有效排班" />
            </section>
          </div></div
      ></template>

      <template v-else-if="['monitor', 'operationsMonitor'].includes(page)"
        ><div class="page-heading">
          <div>
            <h1>设备监测</h1>
            <p>设备测点、运行趋势与异常事件集中查看。</p>
          </div>
          <el-select
            v-model="station"
            clearable
            placeholder="全部场站"
            style="width: 240px"
            ><el-option
              v-for="s in data.stations"
              :key="s.id"
              :label="s.name"
              :value="s.id"
          /></el-select>
        </div>
        <div class="device-grid">
          <section
            class="panel device-card"
            v-for="d in (data.devices || []).filter(
              (x) => !station || x.stationId === station,
            )"
            :key="d.id"
          >
            <div class="panel-title">
              <b>{{ d.name }}</b
              ><el-tag type="success">在线</el-tag>
            </div>
            <p class="subtle">
              {{ labelId(d.stationId) }} · {{ d.deviceCode }}
            </p>
            <div class="device-values">
              <div>
                <label>温度</label
                ><strong :class="{ hot: d.temperature >= d.limit }"
                  >{{ d.temperature }}<small>℃</small></strong
                >
              </div>
              <div>
                <label>有功功率</label
                ><strong>{{ d.power }}<small>kW</small></strong>
              </div>
            </div>
            <svg
              viewBox="0 0 440 100"
              class="sparkline"
              aria-label="采样温度趋势"
            >
              <path d="M0 85H440M0 45H440" stroke="#e7edf6" />
              <path
                :d="sparkline(d)"
                fill="none"
                stroke="#5b78ff"
                stroke-width="3"
              />
            </svg>
            <el-button
              link
              type="primary"
              @click="
                nav(
                  page === 'operationsMonitor'
                    ? 'operationsDevices'
                    : 'devices',
                  d.id,
                )
              "
              >最近采样 {{ fmt(d.sampledAt) }} · 查看记录</el-button
            >
            <div class="device-footer">
              <span>阈值 {{ d.limit }}℃</span
              ><el-button
                type="primary"
                plain
                @click="
                  nav(
                    page === 'operationsMonitor'
                      ? 'operationsDevices'
                      : 'devices',
                    d.id,
                  );
                  openAction('sample', d);
                "
                >更新采样</el-button
              >
            </div>
          </section>
        </div></template
      >

      <template v-else-if="['calendar', 'shifts'].includes(page) && !current"
        ><div class="page-heading">
          <div>
            <h1>{{ title }}</h1>
            <p>跨日班次连续归属；按人员顺序循环排班。</p>
          </div>
          <div class="inline-actions" v-if="page === 'shifts'">
            <el-button @click="openRoster('leave')">休假调班</el-button
            ><el-button @click="openEditor('shifts')">单日设置</el-button
            ><el-button type="primary" @click="openRoster('bulk')"
              >批量设置</el-button
            >
          </div>
        </div>
        <div class="filters mb">
          <el-select
            v-model="station"
            clearable
            placeholder="全部场站"
            style="width: 240px"
            ><el-option
              v-for="s in data.stations"
              :key="s.id"
              :value="s.id"
              :label="s.name"
          /></el-select>
        </div>
        <div class="calendar-grid">
          <section class="panel">
            <el-calendar v-model="calendarDate"
              ><template #date-cell="{ data: cell }"
                ><div class="calendar-cell" @click="selectedDate = cell.day">
                  <span>{{ cell.day.slice(-2) }}</span>
                  <div
                    v-for="s in shiftRows.filter(
                      (x) =>
                        x.startAt < cell.day + 'T23:59:59' &&
                        x.endAt > cell.day + 'T00:00:00',
                    )"
                    :key="s.id"
                    class="shift-chip"
                    @click.stop="selectedDate = cell.day"
                  >
                    <b
                      >{{
                        s.startAt.slice(0, 10) !== cell.day
                          ? "↳ 上日延续 · "
                          : ""
                      }}{{ s.name }} · {{ labelId(s.assigneeId) }}</b
                    ><small
                      >{{ s.startAt.slice(11, 16) }}—{{
                        s.endAt.slice(0, 10) !== cell.day ? "次日" : ""
                      }}{{ s.endAt.slice(11, 16) }}</small
                    >
                  </div>
                </div></template
              ></el-calendar
            >
          </section>
          <section class="panel">
            <div class="panel-title">
              <b>{{ selectedDate }} 班次</b>
            </div>
            <div class="shift-detail" v-for="s in dayShifts" :key="s.id">
              <h3>{{ s.name }} · {{ labelId(s.assigneeId) }}</h3>
              <p>{{ fmt(s.startAt) }}<br />至 {{ fmt(s.endAt) }}</p>
              <el-tag :type="tagType(s.status)">{{ st(s.status) }}</el-tag>
              <div class="inline-actions">
                <el-button link type="primary" @click="nav('logs', s.id)"
                  >查看班次日志</el-button
                ><el-button
                  v-if="s.startAt > data.now"
                  link
                  @click="openEditor('shifts', s)"
                  >单日修改</el-button
                >
              </div>
            </div>
            <el-empty
              v-if="!dayShifts.length"
              description="当天未排班"
            /><el-alert
              title="到点自动交班；交代待补不会阻止下班次进入。重叠班次会提示冲突。"
              type="info"
              :closable="false"
            />
          </section></div
      ></template>

      <CurrentLog
        v-else-if="
          page === 'currentLog' ||
          (page === 'logs' && current?.kind === 'shifts')
        "
        :key="page + recordId"
        :data="data"
        :user="user"
        :initial-shift-id="current?.kind === 'shifts' ? current.id : ''"
        @view="gotoRecord"
        @changed="refresh"
      />
      <template v-else-if="page === 'workflow' || page === 'guide'"
        ><div class="page-heading">
          <div>
            <h1>{{ title }}</h1>
            <p>固定流程与职责来源，只读展示。</p>
          </div>
        </div>
        <Glossary v-if="page === 'guide'" />
        <section
          class="panel"
          v-for="w in [
            [
              '三类工作票',
              [
                '编制',
                '签发人签发',
                '工作负责人确认',
                '工作许可人许可',
                '执行 / 正式打印',
                '终结补录',
              ],
            ],
            [
              '工作联系单',
              ['编制', '票上负责人签发', '执行 / 正式打印', '终结补录'],
            ],
            [
              '操作票',
              ['草稿 / 编制', '发令人签发', '逐项执行 / 正式打印', '终结补录'],
            ],
            [
              '缺陷与消缺',
              [
                '缺陷审批',
                '推荐与调度派发',
                '接单开工',
                '处置提交',
                '站长 / 值长验收',
                '关闭',
              ],
            ],
            [
              '定检 / 巡检',
              [
                '规则触发',
                '派当班负责人 / 缺班补派',
                '设备 / 点项执行',
                '负责人完成',
              ],
            ],
          ]"
          :key="w[0]"
        >
          <div class="panel-title">
            <b>{{ w[0] }}</b>
          </div>
          <el-steps :active="w[1].length" finish-status="success" align-center
            ><el-step v-for="s in w[1]" :key="s" :title="s"
          /></el-steps>
        </section>
        <section class="panel" v-if="page === 'guide'">
          <h2>业务对象与退回规则</h2>
          <p>
            缺陷审批退回：补充资料后重新提交。票据退回：编制人修改后从签发重走，保留历史版本。消缺验收退回：返回现场补充处置。
          </p>
          <p>
            站长 → 值长 →
            班次负责人为业务职责关系。系统不实施复杂授权隔离；身份切换仍保留操作人员和时间记录。
          </p>
          <p>操作票项目按场站规程编制，人员与过程记录可追溯。</p>
        </section></template
      >

      <template v-else-if="current"
        ><div class="page-heading">
          <div>
            <el-button
              link
              @click="
                recordId = '';
                location.hash = '/' + page;
              "
              >← 返回列表</el-button
            >
            <h1>
              {{ current.name }}
              <el-tag :type="tagType(current.status)">{{
                st(current.status)
              }}</el-tag>
            </h1>
            <p>
              {{ current.code }} · {{ labelId(current.stationId) }} ·
              {{
                current.deviceId
                  ? labelId(current.deviceId)
                  : current.deviceName
              }}
            </p>
          </div>
          <div class="inline-actions">
            <el-button
              v-if="canEdit(current)"
              @click="openEditor(current.kind, current)"
              >编辑 / 补充</el-button
            ><template v-if="current.kind?.endsWith('Tickets')"
              ><el-button @click="printTicket(true)">草稿预览</el-button
              ><el-button
                type="primary"
                plain
                :disabled="
                  !['executing', 'terminated'].includes(current.status)
                "
                @click="printTicket(false)"
                >正式打印 / PDF</el-button
              ></template
            ><el-button
              v-if="current.kind.endsWith('Rules')"
              @click="previewRule(current)"
              >触发预览</el-button
            >
          </div>
        </div>
        <section class="panel process-panel" v-if="steps.length">
          <el-steps :active="stageIndex" align-center finish-status="success"
            ><el-step v-for="s in steps" :key="s" :title="s"
          /></el-steps>
        </section>
        <el-alert
          v-if="current.returnReason"
          :title="'退回要求：' + current.returnReason"
          type="warning"
          :closable="false"
          class="mb"
        /><el-alert
          v-if="overdue(current)"
          title="本工单已超时；挂起保留原期限，延期须单独记录。"
          type="warning"
          :closable="false"
          class="mb"
        />
        <div class="detail-layout">
          <div>
            <section class="panel">
              <el-tabs :model-value="detailTab" @tab-change="viewTab"
                ><el-tab-pane label="业务信息" name="overview" /><el-tab-pane
                  v-if="
                    current.kind === 'orders' ||
                    current.kind.endsWith('Tickets')
                  "
                  label="执行 / 处置"
                  name="execution" /><el-tab-pane
                  label="关联单据"
                  name="relations" /><el-tab-pane
                  v-if="current.kind === 'orders'"
                  label="物料领用"
                  name="materials" /><el-tab-pane
                  label="附件证据"
                  name="evidence" /><el-tab-pane
                  label="操作记录"
                  name="history"
              /></el-tabs>
              <template v-if="detailTab === 'overview'"
                ><BusinessContext
                  :record="current"
                  :data="data"
                  @view="gotoRecord"
                /><el-descriptions :column="2" border
                  ><el-descriptions-item
                    v-for="k in detailFields"
                    :key="k"
                    :label="fieldLabel(k)"
                    >{{ val(current, k) }}</el-descriptions-item
                  ></el-descriptions
                ><template
                  v-if="current.kind === 'inspectionRules' && current.points"
                  ><div class="section-title">巡检线路 / 点项结构</div>
                  <div
                    v-for="p in current.points"
                    :key="p.id"
                    class="point-summary"
                  >
                    <b
                      >{{ p.name }} ·
                      {{
                        (p.deviceIds || [p.deviceId]).map(labelId).join("、")
                      }}</b
                    >
                    <p v-for="it in p.items" :key="it.id">
                      {{ it.name }} · {{ typ(it.type) }} {{ it.unit }}
                    </p>
                  </div></template
                ><template
                  v-if="
                    current.kind === 'maintenanceRules' && current.deviceIds
                  "
                  ><div class="section-title">检查设备</div>
                  <el-tag
                    v-for="id in current.deviceIds"
                    :key="id"
                    class="mr"
                    >{{ labelId(id) }}</el-tag
                  ></template
                ><template
                  v-if="current.kind.endsWith('Rules') && current.times"
                  ><div class="section-title">时间规则交集</div>
                  <p>
                    月份 {{ current.months?.join("、") || "不限" }} / 周次
                    {{ current.weeks?.join("、") || "不限" }} / 星期
                    {{ current.weekdays?.join("、") || "不限" }} / 日号
                    {{ current.days?.join("、") || "不限" }} / 时点
                    {{ current.times.join("、") }}
                  </p></template
                ><template v-if="current.hazards"
                  ><div class="section-title">危险点引用快照</div>
                  <el-table :data="current.hazards"
                    ><el-table-column label="辨识 / 危险点"
                      ><template #default="{ row }">{{
                        row.analysis || row.risk
                      }}</template></el-table-column
                    ><el-table-column
                      label="危险后果"
                      prop="consequence" /><el-table-column
                      label="控制措施"
                      prop="measures" /></el-table></template
                ><template
                  v-if="
                    current.kind === 'orders' && current.type === 'corrective'
                  "
                  ><div class="section-title">处置结果摘要</div>
                  <p>原因：{{ current.rootCause || "待现场填写" }}</p>
                  <p>处置：{{ current.work || "待现场填写" }}</p>
                  <p v-if="current.submittedAt">
                    恢复 {{ current.recoveryTemp }}℃ /
                    {{ current.recoveryPower }}kW，连续观察
                    {{ current.observation }} 分钟。
                  </p>
                  <el-button type="primary" plain @click="viewTab('execution')"
                    >查看 / 填写处置与恢复证据</el-button
                  ></template
                ></template
              >
              <template v-if="detailTab === 'execution'"
                ><template
                  v-if="
                    current.kind === 'orders' && current.type === 'corrective'
                  "
                  ><el-alert
                    title="先保存处置过程，再提交验收；验收通过前关联作业票据须全部终结。"
                    type="info"
                    :closable="false"
                  /><el-form label-position="top"
                    ><div class="inline-actions mt">
                      <el-button
                        @click="fillWork"
                        :disabled="current.status !== 'working'"
                        >填入处置参考内容</el-button
                      >
                    </div>
                    <FormFields
                      :model="workForm"
                      :data="data"
                      :fields="[
                        {
                          key: 'rootCause',
                          label: '故障原因',
                          type: 'textarea',
                        },
                        { key: 'work', label: '处置过程', type: 'textarea' },
                        {
                          key: 'recoveryTemp',
                          label: '恢复温度 ℃',
                          type: 'number',
                        },
                        {
                          key: 'recoveryPower',
                          label: '恢复功率 kW',
                          type: 'number',
                        },
                        {
                          key: 'observation',
                          label: '稳定观察分钟',
                          type: 'number',
                        },
                      ]"
                    />
                    <div class="section-title">处置证据</div>
                    <el-upload
                      :show-file-list="false"
                      :before-upload="(f) => uploadFile(f, workForm.evidence)"
                      ><el-button :disabled="current.status !== 'working'"
                        >上传照片 / 记录</el-button
                      ></el-upload
                    ><el-button
                      class="mt"
                      :disabled="current.status !== 'working'"
                      @click="
                        workForm.evidence.push({
                          type: 'note',
                          name: '复测记录：设备已恢复，连续观察30分钟',
                        })
                      "
                      >添加复测记录</el-button
                    >
                    <p v-for="(e, i) in workForm.evidence" :key="i">
                      <a v-if="e.url" :href="e.url" target="_blank">{{
                        e.name
                      }}</a
                      ><span v-else>{{ e.name }}</span>
                    </p>
                    <el-button
                      type="primary"
                      :disabled="current.status !== 'working'"
                      @click="saveWork"
                      >保存处置过程</el-button
                    ></el-form
                  ></template
                >
                <template v-else-if="current.kind === 'orders'"
                  ><div class="execution-header">
                    <b>设备 / 巡检点执行</b
                    ><el-select
                      v-model="pointFilter"
                      clearable
                      placeholder="全部巡检点"
                      style="width: 220px"
                      ><el-option
                        v-for="p in [
                          ...new Set(
                            (current.items || [])
                              .map((x) => x.point)
                              .filter(Boolean),
                          ),
                        ]"
                        :key="p"
                        :label="p"
                        :value="p"
                    /></el-select>
                  </div>
                  <el-table
                    :data="
                      (current.items || []).filter(
                        (x) => !pointFilter || x.point === pointFilter,
                      )
                    "
                    ><el-table-column label="点 / 设备 / 项目" min-width="240"
                      ><template #default="{ row }"
                        ><b>{{ row.point || labelId(row.deviceId) }}</b
                        ><small
                          >{{ labelId(row.deviceId) }} ·
                          {{
                            data.devices?.find((x) => x.id === row.deviceId)
                              ?.deviceCode
                          }}</small
                        >
                        <p>{{ row.name }} {{ row.unit }}</p></template
                      ></el-table-column
                    ><el-table-column label="类型" width="80"
                      ><template #default="{ row }">{{
                        typ(row.type)
                      }}</template></el-table-column
                    ><el-table-column label="本次结果" min-width="180"
                      ><template #default="{ row }"
                        ><el-input
                          v-model="row.result"
                          :disabled="current.status !== 'working'"
                          placeholder="记录值或观察结果"
                        /><el-checkbox
                          v-model="row.abnormal"
                          :disabled="current.status !== 'working'"
                          >发现异常</el-checkbox
                        ></template
                      ></el-table-column
                    ><el-table-column label="执行情况" width="150"
                      ><template #default="{ row }"
                        ><el-tag :type="row.executed ? 'success' : 'info'">{{
                          row.executed ? "已执行" : "未完成"
                        }}</el-tag
                        ><small>{{ fmt(row.executedAt) }}</small></template
                      ></el-table-column
                    ><el-table-column label="操作" width="150"
                      ><template #default="{ row }"
                        ><el-button
                          link
                          type="primary"
                          :disabled="current.status !== 'working'"
                          @click="saveItem(row)"
                          >保存结果</el-button
                        ><el-button
                          v-if="row.defectId"
                          link
                          @click="gotoRecord(row.defectId)"
                          >查看缺陷</el-button
                        ><el-button
                          v-else-if="row.abnormal"
                          link
                          type="warning"
                          @click="
                            openAction('linkDefect', current, {
                              itemId: row.id,
                              deviceId: row.deviceId,
                            })
                          "
                          >登记缺陷</el-button
                        ></template
                      ></el-table-column
                    ></el-table
                  ><el-alert
                    title="负责人完成前须填齐必做项、登记异常，并终结本次关联作业票据；关联新缺陷可独立后续消缺。"
                    type="info"
                    :closable="false"
                    class="mt"
                /></template>
                <template v-else-if="current.kind.endsWith('Tickets')"
                  ><div class="section-title">
                    {{
                      current.kind === "operationTickets"
                        ? "操作项目逐项执行"
                        : "安全措施逐项执行"
                    }}
                  </div>
                  <el-table
                    :data="
                      current.kind === 'operationTickets'
                        ? current.items
                        : current.measures
                    "
                    ><el-table-column type="index" width="60" /><el-table-column
                      label="分组 / 项目"
                      min-width="280"
                      ><template #default="{ row }"
                        ><small>{{ row.group }}</small
                        >{{ row.name }}</template
                      ></el-table-column
                    ><el-table-column label="执行状态" width="130"
                      ><template #default="{ row }"
                        ><el-tag :type="row.executed ? 'success' : 'info'">{{
                          row.executed ? "已执行" : "未完成"
                        }}</el-tag></template
                      ></el-table-column
                    ><el-table-column label="时间 / 人员" width="185"
                      ><template #default="{ row }"
                        >{{ fmt(row.executedAt)
                        }}<small>{{ labelId(row.executedBy) }}</small></template
                      ></el-table-column
                    ><el-table-column label="操作" width="105"
                      ><template #default="{ row }"
                        ><el-button
                          link
                          type="primary"
                          :disabled="
                            current.status !== 'executing' || row.executed
                          "
                          @click="executeStep(row)"
                          >登记执行</el-button
                        ></template
                      ></el-table-column
                    ></el-table
                  >
                  <div class="section-title">危险点 / 预控执行情况</div>
                  <el-table :data="current.hazards || []"
                    ><el-table-column label="危险点"
                      ><template #default="{ row }">{{
                        row.analysis || row.risk
                      }}</template></el-table-column
                    ><el-table-column
                      prop="measures"
                      label="预控措施"
                    /><el-table-column label="执行时间 / 人员"
                      ><template #default="{ row }"
                        >{{ fmt(row.executedAt) }} ·
                        {{ labelId(row.executedBy) }}</template
                      ></el-table-column
                    ><el-table-column label="操作" width="115"
                      ><template #default="{ row }"
                        ><el-button
                          link
                          type="primary"
                          :disabled="
                            current.status !== 'executing' || row.executed
                          "
                          @click="executeHazard(row)"
                          >{{ row.executed ? "已确认" : "确认执行" }}</el-button
                        ></template
                      ></el-table-column
                    ></el-table
                  >
                  <div class="section-title">
                    人员变更 / 延期 / 试运行等过程
                  </div>
                  <el-button
                    type="primary"
                    plain
                    :disabled="current.status !== 'executing'"
                    @click="openAction('process')"
                    >登记作业过程</el-button
                  ><el-table :data="current.processRecords || []" class="mt"
                    ><el-table-column type="expand"
                      ><template #default="{ row }"
                        ><el-descriptions :column="2" border
                          ><el-descriptions-item
                            v-for="key in [
                              'oldLeaderId',
                              'newLeaderId',
                              'oldMemberIds',
                              'newMemberIds',
                              'eventAt',
                              'approvalAt',
                              'approvedBy',
                              'validUntil',
                              'trialContent',
                              'restorationState',
                              'tagLockNumbers',
                              'isolationState',
                            ].filter(
                              (k) => row[k] !== undefined && row[k] !== '',
                            )"
                            :key="key"
                            :label="labels[key] || key"
                            >{{
                              Array.isArray(row[key])
                                ? row[key].map(labelId).join("、")
                                : val(row, key)
                            }}</el-descriptions-item
                          ></el-descriptions
                        ></template
                      ></el-table-column
                    ><el-table-column
                      label="类型"
                      prop="type"
                    /><el-table-column
                      label="内容"
                      prop="description"
                      min-width="300"
                    /><el-table-column
                      label="时间"
                      prop="time"
                    /><el-table-column label="登记人"
                      ><template #default="{ row }">{{
                        labelId(row.actor)
                      }}</template></el-table-column
                    ></el-table
                  ></template
                ></template
              >
              <template v-if="detailTab === 'relations'"
                ><el-alert
                  title="以下显示业务来源及关联作业单据；票据状态与工单状态分别管理。"
                  :closable="false"
                  type="info"
                /><el-table
                  :data="linked"
                  class="click-table"
                  @row-click="(r) => gotoRecord(r.id)"
                  ><el-table-column label="类型" width="130"
                    ><template #default="{ row }">{{
                      kinds[row.kind]
                    }}</template></el-table-column
                  ><el-table-column label="标题 / 编号"
                    ><template #default="{ row }"
                      >{{ row.name }}<small>{{ row.code }}</small></template
                    ></el-table-column
                  ><el-table-column label="状态" width="160"
                    ><template #default="{ row }"
                      ><el-tag :type="tagType(row.status)">{{
                        st(row.status)
                      }}</el-tag></template
                    ></el-table-column
                  ><el-table-column label="操作" width="80"
                    ><template #default="{ row }"
                      ><el-button
                        link
                        type="primary"
                        @click.stop="gotoRecord(row.id)"
                        >查看</el-button
                      ></template
                    ></el-table-column
                  ></el-table
                >
                <div
                  v-if="
                    current.kind === 'orders' &&
                    ['ready', 'working', 'pendingAcceptance'].includes(
                      current.status,
                    )
                  "
                  class="inline-actions mt"
                >
                  <el-button
                    @click="
                      openEditor('workTickets', null, {
                        orderIds: [current.id],
                        stationId: current.stationId,
                      })
                    "
                    >编制关联工作票</el-button
                  ><el-button
                    @click="
                      openEditor('operationTickets', null, {
                        orderIds: [current.id],
                        stationId: current.stationId,
                      })
                    "
                    >编制关联操作票</el-button
                  >
                </div></template
              >
              <template v-if="detailTab === 'materials'"
                ><el-table :data="current.materials || []"
                  ><el-table-column
                    label="物料编号"
                    prop="code" /><el-table-column
                    label="名称"
                    prop="name" /><el-table-column
                    label="数量"
                    prop="quantity" /><el-table-column
                    label="单位"
                    prop="unit" /><el-table-column
                    label="单价"
                    prop="price" /><el-table-column
                    label="金额"
                    prop="total" /><el-table-column label="领用日" prop="date"
                /></el-table>
                <p class="subtle">
                  仅登记领用，金额由数量×单价计算，不扣减实际库存。
                </p></template
              >
              <template v-if="detailTab === 'evidence'"
                ><div
                  v-for="(e, i) in current.evidence || []"
                  :key="i"
                  class="evidence"
                >
                  <a v-if="e.url" :href="e.url" target="_blank">{{ e.name }}</a
                  ><span v-else
                    >{{ e.name }} <el-tag size="small">模拟记录</el-tag></span
                  >
                </div>
                <div v-if="current.termination">
                  <h3>终结说明</h3>
                  <p>{{ current.termination.description }}</p>
                  <el-descriptions :column="2" border
                    ><el-descriptions-item
                      v-for="key in [
                        'workEndAt',
                        'ticketEndAt',
                        'guardianId',
                        'siteRestored',
                        'personnelWithdrawn',
                        'riskAcknowledged',
                        'removedGroundLines',
                        'openedGroundSwitches',
                      ].filter((k) => current.termination[k] !== undefined)"
                      :key="key"
                      :label="labels[key]"
                      >{{ val(current.termination, key) }}</el-descriptions-item
                    ></el-descriptions
                  >
                  <a
                    v-for="id in current.termination.attachmentIds"
                    :key="id"
                    :href="'/api/uploads/' + id"
                    target="_blank"
                    class="evidence"
                    >票据终结附件 · 查看</a
                  >
                </div>
                <el-empty
                  v-if="!current.evidence?.length && !current.termination"
                  description="暂无附件或证据"
              /></template>
              <HistoryPanel
                v-if="detailTab === 'history'"
                :record="current"
                :history="history"
                :data="data"
              />
            </section>
          </div>
          <aside>
            <section class="panel action-panel">
              <div class="panel-title">
                <b>当前业务操作</b><el-tag size="small">{{ user.role }}</el-tag>
              </div>
              <div class="node-routing">
                <b>本节点建议处理人</b>
                <p>
                  {{
                    handlers(current, data).ids.map(labelId).join("、") ||
                    "职责名单待维护"
                  }}
                </p>
                <small>{{ handlers(current, data).source }}</small>
              </div>
              <el-alert
                v-if="current.status === 'review' && blockedTickets.length"
                title="关联作业票据尚未全部终结，暂不可验收通过。"
                type="warning"
                :closable="false"
              />
              <div
                v-if="current.status === 'pendingDispatch'"
                class="recommend"
              >
                <b>系统推荐</b>
                <p>{{ recommendation.text }}</p>
              </div>
              <div
                v-if="current.status === 'ready' && current.requiresPermit"
                class="hint"
              >
                需许可的作业，请先在关联单据中核验工作票 / 操作票。
              </div>
              <el-button
                v-for="(a, i) in actions(current)"
                :key="a"
                class="full"
                :type="
                  ['return', 'reject', 'void'].includes(a)
                    ? 'danger'
                    : i === 0
                      ? 'primary'
                      : 'default'
                "
                :plain="i !== 0"
                :disabled="
                  a === 'approve' &&
                  current.kind === 'orders' &&
                  blockedTickets.length > 0
                "
                @click="openAction(a)"
                >{{ actionLabel(a) }}</el-button
              ><el-button
                v-if="current.kind === 'duties'"
                type="danger"
                plain
                class="full"
                @click="deleteDuty(current)"
                >删除职责记录</el-button
              ><el-empty
                v-if="!actions(current).length"
                :image-size="55"
                description="当前无待处理操作"
              />
              <p class="subtle">
                所有操作保留业务人员、时间与历史。可切换业务身份协同处理。
              </p>
            </section>
            <section class="panel" v-if="current.kind === 'orders'">
              <div class="panel-title"><b>本次作业票据</b></div>
              <div
                v-for="t in (current.ticketIds || [])
                  .map((id) => all.find((x) => x.id === id))
                  .filter(Boolean)"
                :key="t.id"
                class="ticket-check"
                @click="gotoRecord(t.id)"
              >
                <b>{{ t.code }}</b>
                <p>
                  <el-tag
                    :type="t.status === 'terminated' ? 'success' : 'warning'"
                    >{{ st(t.status) }}</el-tag
                  >
                </p>
              </div>
              <p class="subtle" v-if="!current.ticketIds?.length">
                尚未关联作业票据；若需要许可，请先编制关联票据。
              </p>
              <p class="subtle">
                作废不代替本次作业终结；历史参考单据不自动代表作业完成。
              </p>
            </section>
          </aside>
        </div></template
      >

      <template v-else
        ><div class="page-heading">
          <div>
            <h1>{{ title }}</h1>
            <p>
              {{
                page === "acceptance"
                  ? "仅消缺进入独立验收，定检/巡检由负责人完成。"
                  : "按场站、状态与责任人追踪业务，全流程记录可查。"
              }}
            </p>
          </div>
          <div class="inline-actions">
            <template v-if="kind === 'duties'"
              ><el-button @click="downloadCSV">模板下载</el-button
              ><el-button
                @click="
                  importText =
                    '年份,场站,职责类型,姓名\n2026,芳草路光伏电站,工作负责人,李明';
                  importPreview = [];
                  importDialog = true;
                "
                >批量导入</el-button
              ></template
            ><el-button
              v-if="
                fields[kind] &&
                !['people', 'orders', 'alerts', 'notes'].includes(kind)
              "
              type="primary"
              @click="openEditor(kind)"
              ><el-icon><Plus /></el-icon
              >{{
                kind === "defects" ? "人工报障 / 新增缺陷" : "新增"
              }}</el-button
            ><el-button @click="refresh">刷新</el-button>
          </div>
        </div>
        <BusinessList
          :key="page"
          :kind="kind"
          :page="page"
          :data="data"
          :user="user"
          @view="
            (r) => {
              if (r.kind === 'messages') readMessage(r);
              else {
                nav(page, r.id);
              }
            }
          "
          @preview="previewRule"
          @toggle="(r) => openAction('toggle', r)"
        />
      </template>
      <footer>
        NEOMS v{{ data.version }} 新能源场站运行管理
        <span>业务数据 · {{ data.now?.slice(0, 10) }}业务时间</span>
      </footer>
    </main>

    <el-dialog
      v-model="editing"
      :title="(draft.id ? '编辑 / 补充' : '新增') + ' · ' + kinds[editKind]"
      width="min(1140px,94vw)"
      top="4vh"
      class="large-dialog"
      destroy-on-close
      ><RecordEditor
        :model="draft"
        :kind="editKind"
        :data="data"
        :user="user"
        @preview="previewRule"
        ><template #evidence
          ><el-upload :show-file-list="false" :before-upload="uploadDraft"
            ><el-button>上传照片 / 证据</el-button></el-upload
          >
          <p v-for="(e, i) in draft.evidence || []" :key="i">
            {{ e.name }}
          </p></template
        ></RecordEditor
      ><template #footer
        ><el-button v-if="editKind.endsWith('Tickets')" @click="fillTicket"
          >快速填入编制内容</el-button
        ><el-button @click="editing = false">取消</el-button
        ><el-button type="primary" :loading="loading" @click="saveEditor"
          >保存{{ editKind.endsWith("Tickets") ? "编制内容" : "" }}</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="actionDialog"
      :title="actionLabel(actionKey)"
      width="min(720px,94vw)"
      destroy-on-close
      ><template
        v-if="actionKey === 'dispatch' && current?.type === 'corrective'"
        ><el-alert
          :title="recommendation.text"
          type="info"
          :closable="false"
          class="mb" /></template
      ><el-form label-position="top"
        ><FormFields
          :fields="actionFields[actionKey] || []"
          :model="actionModel"
          :data="data"
          :user="user" /><TicketExtra
          v-if="['process', 'terminate'].includes(actionKey)"
          :action="actionKey"
          :model="actionModel"
          :ticket="current"
          :data="data"
          :user="user" /><template
          v-if="actionKey === 'approve' && current?.kind === 'orders'"
          ><el-checkbox v-model="actionModel.checked"
            >已核验处置过程、恢复测点与提交证据</el-checkbox
          ><el-form-item label="验收结论"
            ><el-input
              v-model="actionModel.conclusion"
              type="textarea"
              :rows="4" /></el-form-item
          ><el-button
            link
            @click="
              actionModel.conclusion =
                '处置和恢复证据完整，关联作业票据已终结，同意验收。'
            "
            >填入验收参考意见</el-button
          ></template
        ><template
          v-if="actionKey === 'approve' && current?.status === 'pendingSign'"
          ><FormFields
            :fields="[
              { key: 'approvedStart', label: '批准开始时间', type: 'datetime' },
              { key: 'approvedEnd', label: '批准结束时间', type: 'datetime' },
            ]"
            :model="actionModel"
            :data="data" /></template
        ><template
          v-if="
            actionKey === 'approve' &&
            ['pendingConfirm', 'pendingPermit'].includes(current?.status)
          "
          ><el-checkbox v-model="actionModel.checked"
            >已核验本节点相关措施、工作范围及人员信息</el-checkbox
          ><el-input
            v-model="actionModel.comment"
            type="textarea"
            :rows="3"
            placeholder="本节点确认说明（选填）" /></template
        ><template v-if="actionKey === 'confirm'"
          ><div class="section-title">缺陷证据</div>
          <el-upload
            :show-file-list="false"
            :before-upload="(f) => uploadFile(f, actionUploads)"
            ><el-button>上传研判照片 / 证据</el-button></el-upload
          >
          <p v-for="f in actionUploads" :key="f.id">{{ f.name }}</p></template
        ><template v-if="actionKey === 'terminate'"
          ><div class="section-title">票据终结附件 *</div>
          <el-upload
            :show-file-list="false"
            :before-upload="(f) => uploadFile(f, actionUploads)"
            ><el-button>上传已填写的票据附件</el-button></el-upload
          ><el-button class="mt" plain @click="mockAttachment"
            >生成终结记录附件</el-button
          >
          <p v-for="f in actionUploads" :key="f.id">{{ f.name }}</p></template
        ><el-alert
          v-if="['return', 'reject', 'void', 'pause'].includes(actionKey)"
          title="处理原因将保存在操作历史中。"
          :closable="false"
          type="info" /></el-form
      ><template #footer
        ><el-button @click="actionDialog = false">取消</el-button
        ><el-button type="primary" :loading="loading" @click="commitAction"
          >确认{{ actionLabel(actionKey) }}</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="previewDialog"
      title="规则触发预览 · 条件取交集"
      width="760px"
      ><el-table :data="previewRows"
        ><el-table-column label="计划触发"
          ><template #default="{ row }">{{
            fmt(row.time)
          }}</template></el-table-column
        ><el-table-column label="计划负责人"
          ><template #default="{ row }"
            ><span :class="{ hot: !row.assigneeId }">{{
              row.assigneeId ? labelId(row.assigneeId) : "缺班 → 待分配池"
            }}</span></template
          ></el-table-column
        ><el-table-column label="预计完成"
          ><template #default="{ row }">{{
            fmt(row.dueAt)
          }}</template></el-table-column
        ></el-table
      ><el-empty
        v-if="!previewRows.length"
        description="未来一年没有满足交集的时点，请检查规则"
    /></el-dialog>
    <el-dialog v-model="clockDialog" title="时间工具" width="640px"
      ><el-alert
        title="推进时钟会自动触发在用规则及到点交班；单次最多31天。原有记录保留。"
        type="info"
        :closable="false"
      /><el-date-picker
        v-model="clockTime"
        type="datetime"
        value-format="YYYY-MM-DDTHH:mm:ss"
        class="mt"
      />
      <div class="inline-actions mt">
        <el-button @click="advance(addHours(data.now, 1))">推进1小时</el-button
        ><el-button type="primary" @click="advance(clockTime)"
          >推进至所选时刻</el-button
        ><a href="/api/export" download><el-button>导出数据</el-button></a
        ><el-button type="danger" plain @click="reset">重置数据</el-button>
      </div></el-dialog
    >
    <el-dialog
      v-model="rosterDialog"
      :title="
        rosterMode === 'leave'
          ? '休假调班 · 先预览后应用'
          : '批量轮排 · 按人员顺序循环'
      "
      width="min(1040px,94vw)"
      ><el-form label-position="top"
        ><template v-if="rosterMode === 'leave'"
          ><FormFields
            :fields="[
              {
                key: 'shiftId',
                label: '休假班次',
                type: 'ref',
                options: 'shifts',
              },
              { key: 'endAt', label: '调班范围截止时间', type: 'datetime' },
            ]"
            :model="rosterModel"
            :data="data" /></template
        ><template v-else
          ><FormFields
            :fields="[
              {
                key: 'stationId',
                label: '场站',
                type: 'ref',
                options: 'stations',
              },
              { key: 'startDate', label: '开始日期', type: 'date' },
              { key: 'endDate', label: '结束日期', type: 'date' },
              {
                key: 'peopleIds',
                label: '按顺序选择负责人',
                type: 'multiRef',
                options: 'people',
              },
              {
                key: 'chiefId',
                label: '班次值长',
                type: 'ref',
                options: 'people',
              },
            ]"
            :model="rosterModel"
            :data="data"
          /><el-table :data="rosterModel.slots"
            ><el-table-column label="班次"
              ><template #default="{ row }"
                ><el-input v-model="row.name" /></template></el-table-column
            ><el-table-column label="开始"
              ><template #default="{ row }"
                ><el-time-picker
                  v-model="row.start"
                  value-format="HH:mm"
                  format="HH:mm" /></template></el-table-column
            ><el-table-column label="结束"
              ><template #default="{ row }"
                ><el-time-picker
                  v-model="row.end"
                  value-format="HH:mm"
                  format="HH:mm" /></template></el-table-column
            ><el-table-column label="次日结束" width="120"
              ><template #default="{ row }"
                ><el-switch v-model="row.nextDay" /></template></el-table-column
            ><el-table-column width="80"
              ><template #default="{ $index }"
                ><el-button
                  link
                  type="danger"
                  @click="rosterModel.slots.splice($index, 1)"
                  >删除</el-button
                ></template
              ></el-table-column
            ></el-table
          ><el-button
            class="mt"
            @click="
              rosterModel.slots.push({
                name: '新班次',
                start: '08:00',
                end: '20:00',
                nextDay: false,
              })
            "
            >添加班次</el-button
          ></template
        ></el-form
      ><el-table
        v-if="rosterPreview.length"
        :data="rosterPreview"
        height="260"
        class="mt"
        ><el-table-column prop="name" label="班次" /><el-table-column
          prop="startAt"
          label="开始"
        /><el-table-column prop="endAt" label="结束" /><el-table-column
          label="调整前"
          v-if="rosterMode === 'leave'"
          ><template #default="{ row }">{{
            labelId(row.previousAssignee)
          }}</template></el-table-column
        ><el-table-column label="负责人"
          ><template #default="{ row }">{{
            labelId(row.assigneeId)
          }}</template></el-table-column
        ></el-table
      ><template #footer
        ><el-button @click="rosterDialog = false">取消</el-button
        ><el-button @click="rosterDo(false)">预览排班 / 调整结果</el-button
        ><el-button
          type="primary"
          :disabled="!rosterPreview.length"
          @click="rosterDo(true)"
          >确认应用</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="importDialog"
      title="年度职责人员 · 模板导入"
      width="850px"
      ><p>支持UTF-8 CSV（可用Excel编辑），列为：年份、场站、职责类型、姓名。</p>
      <input
        type="file"
        accept=".csv"
        @change="
          async (e) => {
            importText = await e.target.files[0].text();
            importPreview = [];
          }
        "
      /><el-input
        class="mt"
        v-model="importText"
        type="textarea"
        :rows="5"
      /><el-table :data="importPreview" class="mt"
        ><el-table-column prop="year" label="年份" /><el-table-column
          label="场站"
          ><template #default="{ row }">{{
            labelId(row.stationId)
          }}</template></el-table-column
        ><el-table-column prop="dutyType" label="职责" /><el-table-column
          label="姓名"
          ><template #default="{ row }">{{
            labelId(row.personId)
          }}</template></el-table-column
        ><el-table-column
          prop="result"
          label="校验结果"
          min-width="170" /></el-table
      ><template #footer
        ><el-button @click="downloadCSV">下载模板</el-button
        ><el-button @click="importDo(false)">校验预览</el-button
        ><el-button
          type="primary"
          :disabled="!importPreview.length"
          @click="importDo(true)"
          >确认导入有效行</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="messagesDialog" title="系统消息" width="760px"
      ><div
        class="message-row"
        v-for="m in [...(data.messages || [])].reverse()"
        :key="m.id"
        @click="readMessage(m)"
      >
        <el-tag :type="m.read ? 'info' : 'warning'">{{
          m.read ? "已读" : "未读"
        }}</el-tag
        ><b>{{ m.name }}</b
        ><span>{{ fmt(m.createdAt) }}</span
        ><el-button link type="primary">查看处理 →</el-button>
      </div>
      <el-empty v-if="!data.messages?.length" description="暂无消息"
    /></el-dialog>
  </div>
</template>
