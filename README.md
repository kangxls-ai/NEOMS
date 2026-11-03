# NEOMS 新能源场站运行管理

Java 21 / Spring Boot / MySQL / Vue 3 / Element Plus 初始演示版本。按原型45页实现运行管理演示，逐项范围与保留简化见 [功能补齐核查表](docs/功能补齐核查表V0.2.md)。本版补充独立列表筛选分页、审批历史、多设备巡检和结构化票据过程，支持光伏逆变器异常从感知、预警、缺陷审批、推荐派发、作业票据、处置到独立验收的闭环。所有业务记录持久化到 MySQL，刷新和重启保留数据。

## 快速运行发布包

1. 安装 Java 21、MySQL 8，创建独立库 `neoms_demo`（utf8mb4），为应用账号授予该库读写和建表权限。
2. 将 `config/application-local.example.yml` 复制为 `config/application-local.yml`，填写本机数据库账号密码。地址不同可在该文件配置 `spring.datasource.url`。真实配置不进入 Git。
3. Windows：双击 `start.cmd`，或运行 `powershell -File start.ps1 -Background`。Linux：`bash start.sh`。macOS：`bash start-macos.command`；需双击时先执行 `chmod +x start-macos.command start.sh`。
4. 访问 **http://localhost:8080**。首次启动自动建表并装载虚构数据。后台启动 Windows 用 `stop.ps1`，Linux/macOS 用 `bash stop.sh`；前台用 Ctrl+C。

可用环境变量：`NEOMS_DB_URL`、`NEOMS_DB_USER`、`NEOMS_DB_PASSWORD`、`NEOMS_PORT`。示例账号为应用登录账号，与数据库账号不同。

| 登录名 | 示例人员 | 业务视角 |
|---|---|---|
| admin | 管理员 | 总览与演示控制 |
| dispatch | 周宁 | 调度研判、派发 |
| field | 李明 | 现场处置、负责人确认 |
| signer | 王磊 | 工作签发 |
| permit | 陈杰 | 工作许可 |
| chief | 赵岚 | 站长 / 值长验收 |

统一演示密码：**`Demo@2026`**。登录后右上角可直接切换业务身份，操作历史记录实际选定身份。本版不实施复杂权限隔离。

## 模块与演示

- 工作台：待办、统计、超时、缺班、交代待补提醒、消息中心。
- 场站信息：4座虚构场站、11台设备、模拟测点与异常聚合。
- 运行管理：缺陷及审批、三类工单、四类工作票、操作票、排班/日志、通知与调度命令、定检规则与巡检线路。
- 系统管理：两类危险点库、年度职责人员模板导入、固定审批流程。

演示从工作台“待研判异常”开始；已有待派发、超时、待验收票据阻断、验收退回和已闭环样例可随时跳转。详见 [演示手册](docs/演示手册.md)。演示时钟初始为 **2026-11-02 09:30**，定时生成及交班通过右上角“推进演示时钟”触发；它不随电脑时间自动前进。重置和导出也在此处。

## 源码构建与验证

需要 Java 21、Maven 3.9+、Node 22.12+（本机 Node 24）。

```powershell
./build.ps1
# Maven 不在 PATH 时：./build.ps1 -Maven 'C:/tools/apache-maven/bin/mvn.cmd'
```

Linux/macOS：`bash build.sh`（可用 `MAVEN=/path/to/mvn` 指定 Maven）。脚本执行 `npm ci`、前端构建、Maven 测试与打包，输出 `release/NEOMS-0.2.1` 及压缩包。为避免混入旧依赖，已有发布目录需先改名后再构建。开发时后端使用外置配置及本地数据库，前端 `cd frontend && npm run dev`，Vite 代理 `/api` 到8080。

自动化：`mvn test` 使用独立内存 H2（MySQL兼容模式），不连接本机数据库。运行中的演示环境可执行 `node scripts/smoke.mjs --reset`，该命令会重置本项目演示数据并跑真实 HTTP/MySQL 闭环，勿指向保留资料的环境。

## 发布目录与局部更新

```text
NEOMS-0.2.1/
  neoms.jar                   # 业务类 + Spring Boot启动器，第三方依赖不内嵌
  lib/*.jar                   # 运行时依赖
  lib-manifest.sha256          # 外置依赖校验清单
  config/application.yml      # Spring Boot外置配置
  config/application-local.yml # 本地连接信息，自行创建，不进Git/通用发布包
  config/loader.properties    # loader.path=lib/
  config/static/              # Vue生产构建
  data/uploads/               # 运行后生成附件，更新时保留
  logs/                       # 运行日志
  start.ps1 / start.cmd / start.sh / start-macos.command
  stop.ps1 / stop.sh
  docs/
```

`PropertiesLauncher` 从外置 `lib` 加载依赖。只改业务代码时停服务、备份并替换 `neoms.jar`、再启动；前端改动替换 `config/static`；依赖变化按清单替换对应 JAR 并移走同库旧版本，避免重复版本。保持 `config/application-local.yml`、MySQL数据、`data/uploads` 不被覆盖。具体见 [部署更新说明](docs/部署更新说明.md)。

## 初始版本边界

本版为领导演示，测点、阈值与操作项目均为标明的虚构数据/可编辑演示模板；未接入真实SCADA设备、外部消息、库存或复杂权限。票据正式打印走浏览器打印/另存为PDF，显示当前版本与签发许可记录；未实现生产票据版式套打和电子签章。过程变更以记录方式保存，无额外审批链。数据库采用独立业务记录加版本历史的简化存储，后续可迁移到规范业务表。详见 [实现覆盖与技术说明](docs/实现覆盖与技术说明.md)。
