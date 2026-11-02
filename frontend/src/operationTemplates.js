export function operationTemplate(first, second, device = "[待填写设备]") {
  const prototype = [
    `接值长令：将${device}由“运行”转“检修”（原型示例）`,
    "在五防电脑上模拟操作核实无误（原型示例）",
    `检查${device}下属风机均已停运（原型示例）`,
    `检查${device}下属箱变均已转到“冷备用”状态（原型示例）`,
    `检查${device}负荷电流为零（原型示例）`,
    `远程遥控断开${device}（原型此项文字不完整，待业务校核）`,
    `检查${device}确在“分闸”位置（原型示例）`,
    `检查${device}柜“远方/就地”把手切至“就地”位置（原型示例）`,
    `断开${device}储能回路空开（原型示例，回路名称待核对）`,
    `断开${device}控制回路空开（原型示例，回路名称待核对）`,
    `将${device}小车由“工作”位置摇至“试验”位置（原型示例）`,
    `检查${device}小车确已摇至“试验”位置（原型示例）`,
    `检查${device}综保装置显示线路侧电压为零（原型示例）`,
  ];
  const names =
    first === "集电线路操作票" && second === "运行转检修"
      ? prototype
      : [
          `${first || "所选票种"} · ${second || "所选转换"}：任务与设备核对（演示占位）`,
          "原型未提供本转换完整项目，请由业务人员补全并校核（演示占位）",
        ];
  return names.map((name) => ({
    id: crypto.randomUUID(),
    name,
    executed: false,
  }));
}
