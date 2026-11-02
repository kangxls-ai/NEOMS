export function handlers(r, data) {
  const people = data.people || [],
    now = data.now || "2026-11-02",
    year = now.slice(0, 4);
  const byRole = (role) =>
    people.filter((x) => (x.role || "").includes(role)).map((x) => x.id);
  const duty = (type) =>
    (data.duties || [])
      .filter(
        (x) =>
          x.status !== "removed" &&
          String(x.year) === year &&
          x.stationId === r.stationId &&
          x.dutyType === type,
      )
      .map((x) => x.personId);
  if (
    [
      "maintenanceRules",
      "inspectionRules",
      "workHazards",
      "operationHazards",
      "duties",
      "stations",
      "devices",
      "teams",
    ].includes(r.kind)
  )
    return { ids: byRole("管理员"), source: "演示环境配置与资料维护" };
  if (r.kind === "defects" && r.status === "pendingApproval") {
    let shift = (data.shifts || []).find(
      (x) => x.stationId === r.stationId && x.startAt <= now && x.endAt > now,
    );
    return {
      ids: shift?.assigneeId ? [shift.assigneeId] : duty("工作负责人"),
      source: shift?.assigneeId
        ? "当前实际班次负责人"
        : "本年度场站工作负责人（当前缺班）",
    };
  }
  if (r.kind === "workTickets") {
    if (r.status === "pendingSign")
      return r.subtype === "contact"
        ? { ids: [r.leaderId], source: "联系单票上工作负责人" }
        : { ids: duty("工作签发人"), source: `${year}年场站工作签发人名单` };
    if (r.status === "pendingConfirm")
      return { ids: [r.leaderId], source: "票上工作负责人确认①" };
    if (r.status === "pendingPermit")
      return {
        ids: duty("工作许可人"),
        source: `${year}年场站工作许可人名单确认②`,
      };
  }
  if (r.kind === "operationTickets" && r.status === "pendingSign")
    return { ids: [r.issuerId], source: "操作票票上发令人" };
  if (r.kind === "orders")
    return r.status === "pendingDispatch" || r.status === "unassigned"
      ? { ids: byRole("调度"), source: "调度确认派发" }
      : r.status === "review"
        ? { ids: byRole("站长"), source: "站长 / 值长独立验收" }
        : { ids: [r.assigneeId], source: "工单责任人" };
  return {
    ids: [r.leaderId || r.assigneeId || r.executorId].filter(Boolean),
    source: "票上 / 任务指定负责人",
  };
}
