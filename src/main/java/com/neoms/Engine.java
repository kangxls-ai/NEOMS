package com.neoms;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Engine {
    final Store db;
    Engine(Store db){this.db=db;}
    static Map<String,Object> m(Object... a){var r=new LinkedHashMap<String,Object>();for(int i=0;i<a.length;i+=2)r.put(a[i].toString(),a[i+1]);return r;}
    static String s(Map<String,Object>d,String k){return Objects.toString(d.get(k),"");}
    static boolean yes(Map<String,Object>d,String k){return Boolean.TRUE.equals(d.get(k));}
    static List<Map<String,Object>> items(Map<String,Object>d,String k){Object a=d.get(k);if(a instanceof List<?> l)return l.stream().filter(Map.class::isInstance).<Map<String,Object>>map(x->new LinkedHashMap<String,Object>((Map<String,Object>)x)).toList();return List.of();}
    static List<String> ids(Map<String,Object>d,String k){Object a=d.get(k);if(a instanceof List<?> l)return l.stream().map(Object::toString).toList();return List.of();}
    static void require(boolean ok,String msg){if(!ok)throw new IllegalArgumentException(msg);}
    static void need(Map<String,Object>d,String...keys){for(String k:keys)require(!s(d,k).isBlank(),"请完整填写："+k);}
    static double num(Map<String,Object>d,String k){try{double v=Double.parseDouble(s(d,k));require(Double.isFinite(v),"数值无效："+k);return v;}catch(NumberFormatException e){throw new IllegalArgumentException("请填写有效数值："+k);}}
    static void stage(Map<String,Object>d,String...allowed){require(Arrays.asList(allowed).contains(s(d,"status")),"当前状态不能执行此操作，请刷新后核对");}
    public String now(){return s(db.read("demo-clock"),"time");}
    private void record(Map<String,Object>d,String actor,String action){db.history(d,actor,action,now());}
    private Map<String,Object> put(Map<String,Object>d,String actor,String action){var r=db.save(d);record(r,actor,action);return r;}
    Map<String,Object> add(String kind,Map<String,Object>d,String actor){d.putIfAbsent("createdAt",now());d.putIfAbsent("createdBy",actor);var r=db.insert(kind,d);record(r,actor,"创建记录");return r;}
    void message(String title,String target,String person){add("messages",m("name",title,"targetId",target,"recipient",person,"read",false),"系统");}
    @Transactional
    public Map<String,Object> create(String kind,Map<String,Object> input,String actor){
        require(!Set.of("settings","messages","people").contains(kind),"该记录由系统维护");var d=new LinkedHashMap<>(input);for(String k:List.of("id","kind","_revision","createdAt","createdBy","orderId","faultId"))d.remove(k);
        d.putIfAbsent("name",kind);d.put("code",prefix(kind)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());
        switch(kind){
            case "defects"->{need(d,"deviceId","description","sourceType");require(Set.of("maintenance","inspection","warning","alarm","manual").contains(s(d,"sourceType")),"缺陷来源无效");if(!s(d,"sourceType").equals("manual")){need(d,"sourceId");var source=db.read(s(d,"sourceId"));boolean event=Set.of("warning","alarm").contains(s(d,"sourceType"));require(event?s(source,"kind").equals("alerts"):s(source,"kind").equals("orders")&&s(source,"type").equals(s(d,"sourceType")),"来源单据与缺陷来源类型不一致");}d.put("status","pendingApproval");}
            case "workTickets","operationTickets"->{d.put("status","draft");d.put("revision",1);d.put("processRecords",List.of());if(kind.equals("operationTickets"))d.put("operatorId",actor);}
            case "maintenanceRules","inspectionRules"->{validateRule(kind,d);d.put("status","active");}
            case "orders"->throw new IllegalArgumentException("工单由缺陷审批或任务规则生成");
            case "shifts"->{need(d,"startAt","endAt","assigneeId");checkShift(d,"");d.put("status","scheduled");}
            case "commands"->{need(d,"description","recipientId","executorId");d.putIfAbsent("issuerId",actor);d.put("status","pendingOperation");}
            case "notices"->{need(d,"description","recipientId");d.put("issuerId",actor);d.put("status","sent");}
            case "notes"->{need(d,"description");var shift=shiftAt(LocalDateTime.parse(now()),s(d,"stationId"));require(shift.isPresent(),"当前时间没有有效班次，请先排班");d.put("shiftId",shift.get().get("id"));}
            case "alerts"->throw new IllegalArgumentException("请从设备监测更新采样");
            case "workHazards"->need(d,"analysis","measures");
            case "operationHazards"->need(d,"risk","consequence","measures");
            case "duties"->need(d,"year","stationId","dutyType","personId");
            default->{}
        }
        if(!s(d,"deviceId").isBlank()&&!s(d,"stationId").isBlank()){var device=db.read(s(d,"deviceId"));require(s(device,"kind").equals("devices")&&s(device,"stationId").equals(s(d,"stationId")),"设备与所属场站不匹配");}var r=add(kind,d,actor);if(kind.equals("commands")||kind.equals("notices"))message(s(r,"name"),s(r,"id"),kind.equals("commands")?s(r,"executorId"):s(r,"recipientId"));linkTicket(r,actor);return r;
    }
    static String prefix(String k){return switch(k){case "orders"->"GD";case "defects"->"QX";case "workTickets"->"GZ";case "operationTickets"->"CZ";case "commands"->"DD";case "notices"->"TZ";default->"NE";};}
    @Transactional
    public Map<String,Object> edit(String id,Map<String,Object> input,String actor){
        var d=db.lock(id);if(input.containsKey("_revision"))require(Objects.toString(input.get("_revision")).equals(Objects.toString(d.get("_revision"))),"记录已更新，请刷新后再编辑");var kind=s(d,"kind");
        require(!Set.of("settings","messages","people","alerts","orders").contains(kind),"请通过对应业务操作更新");
        if(kind.equals("defects")||kind.endsWith("Tickets"))stage(d,"draft","returned");if(kind.equals("shifts"))require(LocalDateTime.parse(s(d,"startAt")).isAfter(LocalDateTime.parse(now())),"历史或已开始班次不能修改");
        var protectedFields=Set.of("id","kind","_revision","status","createdAt","createdBy","code","revision","signedAt","confirmedAt","permittedAt","orderId","faultId","processRecords","operatorId");
        input.forEach((k,v)->{if(!protectedFields.contains(k)&&!(kind.equals("defects")&&Set.of("sourceType","sourceId").contains(k)))d.put(k,v);});if(kind.equals("shifts"))checkShift(d,id);if(kind.endsWith("Rules"))validateRule(kind,d);var r=put(d,actor,"编辑记录");linkTicket(r,actor);return r;
    }
    void linkTicket(Map<String,Object> ticket,String actor){if(!s(ticket,"kind").endsWith("Tickets"))return;for(var raw:db.list("orders")){boolean wanted=ids(ticket,"orderIds").contains(s(raw,"id")),existing=ids(raw,"ticketIds").contains(s(ticket,"id"));if(wanted==existing)continue;var order=db.lock(s(raw,"id"));if(!wanted)require(!Set.of("working","paused","review","closed","completed").contains(s(order,"status")),"工单已开始执行，不能取消本次作业票关联");var a=new ArrayList<>(ids(order,"ticketIds"));if(wanted)a.add(s(ticket,"id"));else a.remove(s(ticket,"id"));order.put("ticketIds",a);put(order,actor,(wanted?"关联":"取消关联")+"作业票据 "+s(ticket,"code"));}for(String id:ids(ticket,"orderIds"))require(s(db.read(id),"kind").equals("orders"),"关联对象不是工单");}
    @Transactional
    public Map<String,Object> act(String id,String action,Map<String,Object> p,String actor){
        var d=db.lock(id);String kind=s(d,"kind");String log=action;
        switch(kind){
            case "devices"->{require(action.equals("sample"),"操作无效");return sample(d,p,actor);}
            case "alerts"->{
                if(action.equals("confirm")){stage(d,"pending");need(p,"reason");require(s(d,"defectId").isBlank(),"该事件已关联缺陷");var fault=create("defects",m("name",s(p,"defectName").isBlank()?s(d,"name"):p.get("defectName"),"deviceId",d.get("deviceId"),"stationId",d.get("stationId"),"sourceType",s(d,"level").equals("alarm")?"alarm":"warning","sourceId",id,"description",s(p,"description").isBlank()?p.get("reason"):p.get("description"),"level",p.getOrDefault("defectLevel","一般缺陷"),"evidence",p.getOrDefault("evidence",List.of())),actor);d.put("defectId",fault.get("id"));d.put("status","converted");log="研判确认并登记待审批缺陷";}
                else if(action.equals("exclude")){stage(d,"pending");need(p,"reason");d.put("status","excluded");d.put("reason",p.get("reason"));log="排除异常："+p.get("reason");}else throw new IllegalArgumentException("操作无效");
            }
            case "defects"->{
                switch(action){
                    case "approve"->{stage(d,"pendingApproval");var old=s(d,"orderId");require(old.isEmpty(),"已有消缺工单，禁止重复生成");var order=add("orders",m("name",s(d,"name"),"code","GD-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"type","corrective","status","pendingDispatch","defectId",id,"deviceId",d.get("deviceId"),"stationId",d.get("stationId"),"description",d.get("description"),"defectSnapshot",new LinkedHashMap<>(d),"requiresPermit",true,"recoveryLimit",db.read(s(d,"deviceId")).getOrDefault("limit",75),"ticketIds",List.of(),"evidence",List.of()),actor);d.put("orderId",order.get("id"));d.put("status","pendingRepair");log="审批通过并生成待派发消缺工单";}
                    case "return"->{stage(d,"pendingApproval");need(p,"reason");d.put("status","returned");d.put("returnReason",p.get("reason"));log="缺陷退回补充："+p.get("reason");}
                    case "resubmit"->{stage(d,"returned");need(d,"deviceId","description");d.put("status","pendingApproval");log="修改后重新提交缺陷审批";}
                    case "void"->{need(p,"reason");require(s(d,"orderId").isBlank(),"已有下游工单，请分别核对工单与票据，不能一键作废链路");stage(d,"pendingApproval","returned");d.put("status","void");d.put("voidReason",p.get("reason"));log="独立作废缺陷："+p.get("reason");}
                    default->throw new IllegalArgumentException("操作无效");
                }
            }
            case "orders"->{return order(d,action,p,actor);}
            case "workTickets","operationTickets"->{return ticket(d,action,p,actor);}
            case "maintenanceRules","inspectionRules"->{switch(action){case "toggle"->{d.put("status",s(d,"status").equals("active")?"inactive":"active");log="切换规则启停，历史任务保留";}case "trigger"->{stage(d,"active");return trigger(d,LocalDateTime.parse(now()),actor);}default->throw new IllegalArgumentException("操作无效");}}
            case "commands"->{if(action.equals("operate")){stage(d,"pendingOperation");d.put("status","pendingReply");d.put("operatedAt",now());d.put("operatedBy",actor);log="操作完成，待回令";}else if(action.equals("reply")){stage(d,"pendingReply");d.put("status","ended");d.put("repliedAt",now());d.put("repliedBy",actor);log="调度回令完成";}else throw new IllegalArgumentException("操作无效");}
            case "shifts"->{require(action.equals("handover"),"操作无效");require(!LocalDateTime.parse(now()).isBefore(LocalDateTime.parse(s(d,"endAt"))),"班次尚未结束");need(p,"handover");d.put("handover",p.get("handover"));d.put("handoverAt",now());d.put("status","handedOver");log="补填班后交代，下一班引用同步更新";}
            case "messages"->{require(action.equals("read"),"操作无效");d.put("read",true);}
            default->throw new IllegalArgumentException("该对象没有此业务操作");
        }
        return put(d,actor,log);
    }
    Map<String,Object> order(Map<String,Object>d,String action,Map<String,Object>p,String actor){
        String type=s(d,"type"),log=action;
        switch(action){
            case "dispatch","reassign"->{stage(d,"pendingDispatch","unassigned","pendingAcceptance","ready");need(p,"assigneeId","dueAt");db.read(s(p,"assigneeId"));require(LocalDateTime.parse(s(p,"dueAt")).isAfter(LocalDateTime.parse(now())),"计划完成时间须晚于当前时间");d.putAll(m("assigneeId",p.get("assigneeId"),"teamId",p.getOrDefault("teamId","team-1"),"dueAt",p.get("dueAt"),"status",type.equals("corrective")?"pendingAcceptance":"ready"));log="确认派发 / 改派任务";}
            case "accept"->{stage(d,"pendingAcceptance");d.put("status","ready");d.put("acceptedAt",now());log="现场接单";}
            case "start"->{stage(d,"ready");if(yes(d,"requiresPermit")){var ts=ids(d,"ticketIds");require(!ts.isEmpty(),"本次作业需许可，请先关联作业票据");for(String t:ts){var ticket=db.read(t);require(Set.of("executing","terminated").contains(s(ticket,"status")),"作业票据尚未完成签发或许可："+s(ticket,"code"));}}d.put("status","working");d.put("startedAt",now());log="开始现场执行";}
            case "pause"->{stage(d,"working");need(p,"reason");d.put("status","paused");d.put("pauseReason",p.get("reason"));log="挂起（保留原期限）："+p.get("reason");}
            case "resume"->{stage(d,"paused");d.put("status","working");log="恢复执行，保留原期限";}
            case "transfer"->{stage(d,"working","paused");need(p,"assigneeId","reason");db.read(s(p,"assigneeId"));d.put("assigneeId",p.get("assigneeId"));log="执行中责任移交："+p.get("reason");}
            case "extend"->{stage(d,"working","paused","ready");need(p,"dueAt","reason");require(LocalDateTime.parse(s(p,"dueAt")).isAfter(LocalDateTime.parse(s(d,"dueAt"))),"延期时间须晚于原期限");d.put("dueAt",p.get("dueAt"));log="工单延期："+p.get("reason");}
            case "record"->{stage(d,"working");for(String k:List.of("rootCause","work","recoveryTemp","recoveryPower","observation","evidence","remarks"))if(p.containsKey(k))d.put(k,p.get(k));log="保存处置过程及恢复证据";}
            case "item"->{stage(d,"working");need(p,"itemId","result");var result=new ArrayList<Map<String,Object>>();boolean found=false;for(var it:items(d,"items")){if(s(it,"id").equals(s(p,"itemId"))){it.putAll(m("result",p.get("result"),"executed",true,"executedAt",now(),"executedBy",actor,"abnormal",yes(p,"abnormal")));found=true;}result.add(it);}require(found,"工作项不存在");d.put("items",result);log="登记设备 / 巡检项执行结果";}
            case "submit"->{stage(d,"working");require(type.equals("corrective"),"定检/巡检由负责人提交完成，无独立验收");need(d,"rootCause","work","recoveryTemp","recoveryPower","observation");require(num(d,"observation")>0&&num(d,"recoveryTemp")>=0&&num(d,"recoveryPower")>=0,"恢复数据不完整");require(!items(d,"evidence").isEmpty(),"请上传照片或添加复测记录");d.put("status","review");d.put("executionEndedAt",now());d.put("submittedAt",now());log="处置提交，进入站长 / 值长验收";}
            case "finish"->{stage(d,"working");require(!type.equals("corrective"),"消缺须提交验收");var its=items(d,"items");require(!its.isEmpty()&&its.stream().allMatch(it->yes(it,"executed")&&!s(it,"result").isBlank()),"存在未完成必做项");require(its.stream().filter(it->yes(it,"abnormal")).allMatch(it->!s(it,"defectId").isBlank()),"异常项目须先登记关联缺陷");checkTerminated(d);d.put("status","completed");d.put("executionEndedAt",now());d.put("remarks",p.getOrDefault("remarks",""));d.put("finishedAt",now());log="负责人提交完成，无独立验收";}
            case "approve"->{stage(d,"review");need(p,"conclusion");require(yes(p,"checked"),"请确认已核验处置结果与证据");checkTerminated(d);require(num(d,"recoveryPower")>0,"恢复功率尚未恢复，请退回处置");if(!s(d,"recoveryLimit").isBlank())require(num(d,"recoveryTemp")<num(d,"recoveryLimit"),"恢复温度未满足本设备配置");d.put("status","closed");d.put("conclusion",p.get("conclusion"));d.put("approvedAt",now());d.put("approvedBy",actor);var fault=db.lock(s(d,"defectId"));fault.put("status","repaired");fault.put("closedAt",now());fault.put("evidence",d.getOrDefault("evidence",List.of()));put(fault,actor,"消缺验收通过，缺陷同步关闭");log="验收通过，工单和关联缺陷关闭";}
            case "reject"->{stage(d,"review");need(p,"reason");d.put("status","working");d.put("returnReason",p.get("reason"));log="验收退回现场补充处置："+p.get("reason");}
            case "material"->{stage(d,"ready","working","paused");need(p,"name","code","unit","date");double q=num(p,"quantity"),price=num(p,"price");require(q>0&&price>=0,"数量须大于0，单价不得为负");var ms=new ArrayList<>(items(d,"materials"));ms.add(m("name",p.get("name"),"code",p.get("code"),"unit",p.get("unit"),"date",p.get("date"),"quantity",q,"price",price,"total",Math.round(q*price*100)/100.0));d.put("materials",ms);log="登记物料领用（金额自动计算）";}
            case "linkDefect"->{stage(d,"working");need(p,"itemId","description");require(items(d,"items").stream().anyMatch(x->s(x,"id").equals(s(p,"itemId"))),"工作项不存在");String source=type.equals("inspection")?"inspection":"maintenance";var fault=create("defects",m("name","检查发现："+p.get("description"),"description",p.get("description"),"sourceType",source,"sourceId",d.get("id"),"stationId",d.get("stationId"),"deviceId",p.getOrDefault("deviceId",d.get("deviceId")),"level","一般缺陷"),actor);var its=new ArrayList<Map<String,Object>>();for(var it:items(d,"items")){if(s(it,"id").equals(s(p,"itemId")))it.putAll(m("defectId",fault.get("id"),"abnormal",true));its.add(it);}d.put("items",its);log="执行项登记关联待审批缺陷";}
            default->throw new IllegalArgumentException("操作无效");
        }return put(d,actor,log);
    }
    void checkTerminated(Map<String,Object>d){for(String id:ids(d,"ticketIds")){var t=db.read(id);require(s(t,"status").equals("terminated"),"关联作业票据未终结："+s(t,"code")+"（作废不等于终结）");}}
    Map<String,Object> ticket(Map<String,Object>d,String action,Map<String,Object>p,String actor){
        boolean work=s(d,"kind").equals("workTickets"),contact=s(d,"subtype").equals("contact");String log=action;
        switch(action){
            case "submit"->{stage(d,"draft","returned");need(d,"name","deviceName","description","leaderId","plannedStart","plannedEnd");require(LocalDateTime.parse(s(d,"plannedEnd")).isAfter(LocalDateTime.parse(s(d,"plannedStart"))),"计划结束时间须晚于开始");require(!items(d,"hazards").isEmpty(),"请从相应预控库选择危险点");if(!work){need(d,"firstType","secondType","issuerId","supervisorId");var first=s(d,"firstType");require(Set.of("集电线路操作票","SVG操作票","站用变操作票","接地变操作票","出线操作票","PT操作票").contains(first),"操作票一级类型无效");var types=List.of("运行转检修","检修转运行","运行转冷备用","冷备用转检修","检修转冷备用","冷备用转运行","集电线路下属箱变运行转冷备用","集电线路下属箱变冷备用转运行");require(types.subList(0,first.equals("PT操作票")?2:first.equals("集电线路操作票")?8:6).contains(s(d,"secondType")),"一二级操作票类型不匹配");require(!items(d,"items").isEmpty(),"操作票须有顺序项目");for(var item:items(d,"items"))need(item,"name");}else if(!contact)require(!items(d,"measures").isEmpty(),"请填写本票种安全措施");if(s(d,"status").equals("returned"))d.put("revision",((Number)d.getOrDefault("revision",1)).intValue()+1);d.put("status","pendingSign");log="提交签发（当前版本重新流转）";}
            case "approve"->{stage(d,"pendingSign","pendingConfirm","pendingPermit");String old=s(d,"status");if(!old.equals("pendingSign"))require(yes(p,"checked"),"请核验本节点相关安全措施并确认");String next=old.equals("pendingSign")?(work&&!contact?"pendingConfirm":"executing"):old.equals("pendingConfirm")?"pendingPermit":"executing";d.put("status",next);String phase=old.equals("pendingSign")?"signed":old.equals("pendingConfirm")?"confirmed":"permitted";d.put(phase+"At",now());d.put(phase+"By",actor);d.put(phase+"Note",p.getOrDefault("comment",""));if(phase.equals("signed")){var start=LocalDateTime.parse(s(p,"approvedStart").isBlank()?s(d,"plannedStart"):s(p,"approvedStart"));var end=LocalDateTime.parse(s(p,"approvedEnd").isBlank()?s(d,"plannedEnd"):s(p,"approvedEnd"));require(end.isAfter(start),"批准结束时间须晚于开始时间");d.put("approvedStart",p.getOrDefault("approvedStart",d.get("plannedStart")));d.put("approvedEnd",p.getOrDefault("approvedEnd",d.get("plannedEnd")));}log=old.equals("pendingSign")?"签发通过":old.equals("pendingConfirm")?"工作负责人确认":"工作许可人许可";}
            case "return"->{stage(d,"pendingSign","pendingConfirm","pendingPermit");need(p,"reason");d.put("status","returned");d.put("returnReason",p.get("reason"));for(String k:List.of("signedAt","signedBy","confirmedAt","confirmedBy","permittedAt","permittedBy"))d.remove(k);log="退回编制，修改后从签发重走："+p.get("reason");}
            case "step"->{stage(d,"executing");need(p,"itemId");String key=work?"measures":"items";if(!work){for(var item:items(d,key)){if(s(item,"id").equals(s(p,"itemId")))break;require(yes(item,"executed"),"请按顺序完成前面的操作项目");}}var its=new ArrayList<Map<String,Object>>();boolean found=false;for(var it:items(d,key)){if(s(it,"id").equals(s(p,"itemId"))){it.putAll(m("executed",true,"executedAt",now(),"executedBy",actor,"result",p.getOrDefault("result","已执行")));found=true;}its.add(it);}require(found,"执行项目不存在");d.put(key,its);log="逐项登记措施 / 操作执行";}
            case "process"->{stage(d,"executing");need(p,"processType","description","eventAt");LocalDateTime.parse(s(p,"eventAt"));switch(s(p,"processType")){case "工作负责人变更"->need(p,"oldLeaderId","newLeaderId","approvedBy","approvalAt");case "工作人员变更"->{require(!ids(p,"oldMemberIds").isEmpty()&&!ids(p,"newMemberIds").isEmpty(),"请登记变更前后成员");need(p,"approvedBy","approvalAt");}case "工作延期"->{need(p,"validUntil","approvedBy","approvalAt");require(LocalDateTime.parse(s(p,"validUntil")).isAfter(LocalDateTime.parse(s(d,"plannedEnd"))),"延期有效期须晚于原计划结束");}default->{}}var ps=new ArrayList<>(items(d,"processRecords"));var entry=new LinkedHashMap<>(p);entry.putAll(m("type",p.get("processType"),"time",now(),"actor",actor));ps.add(entry);d.put("processRecords",ps);log="登记作业过程："+p.get("processType");}
            case "hazard"->{stage(d,"executing");need(p,"itemId");var hs=new ArrayList<Map<String,Object>>();boolean found=false;for(var h:items(d,"hazards")){if(s(h,"id").equals(s(p,"itemId"))){h.putAll(m("executed",true,"executedAt",now(),"executedBy",actor));found=true;}hs.add(h);}require(found,"危险点不存在");d.put("hazards",hs);log="危险点与预控措施执行确认";}
            case "terminate"->{stage(d,"executing");need(p,"description","workEndAt","ticketEndAt");require(!LocalDateTime.parse(s(p,"ticketEndAt")).isBefore(LocalDateTime.parse(s(p,"workEndAt"))),"票据终结不能早于工作结束");if(work){require(yes(p,"siteRestored")&&yes(p,"personnelWithdrawn"),"请确认现场恢复及人员撤离");if(contact)require(items(d,"hazards").stream().allMatch(h->yes(h,"executed")),"请先确认联系单危险点预控执行情况");}else{need(p,"guardianId");require(yes(p,"riskAcknowledged"),"请确认危险点已知悉并执行");}require(!ids(p,"attachmentIds").isEmpty(),"请上传票据终结附件");for(String f:ids(p,"attachmentIds"))require(db.jdbc().queryForObject("SELECT COUNT(*) FROM neoms_files WHERE id=?",Integer.class,f)>0,"附件不存在");var required=contact?List.<Map<String,Object>>of():items(d,work?"measures":"items");require(required.stream().allMatch(it->yes(it,"executed")),"仍有未完成措施 / 操作项目，请先逐项记录");d.put("status","terminated");d.put("terminatedAt",now());d.put("terminatedBy",actor);d.put("termination",p);log="终结补录完成，附件已关联";}
            case "void"->{stage(d,"draft","returned","pendingSign","pendingConfirm","pendingPermit");need(p,"reason");d.put("status","void");d.put("voidReason",p.get("reason"));log="独立作废票据："+p.get("reason");}
            default->throw new IllegalArgumentException("操作无效");
        }return put(d,actor,log);
    }
    Map<String,Object> sample(Map<String,Object>dev,Map<String,Object>p,String actor){
        double limit=dev.containsKey("limit")?num(dev,"limit"):75;double value=p.containsKey("temperature")?num(p,"temperature"):83;dev.put("temperature",value);dev.put("power",value>=limit?620:905);var samples=new ArrayList<>(items(dev,"samples"));samples.add(m("time",now(),"temperature",value,"power",dev.get("power"),"unit","℃ / kW"));while(samples.size()>48)samples.removeFirst();dev.put("samples",samples);dev.put("sampledAt",now());put(dev,actor,"模拟测点采样（不控制真实设备）");
        if(value<limit)return dev;var existing=db.list("alerts").stream().filter(a->s(a,"deviceId").equals(s(dev,"id"))&&Set.of("pending","converted").contains(s(a,"status"))).filter(a->{if(s(a,"defectId").isBlank())return true;return !Set.of("repaired","void").contains(s(db.read(s(a,"defectId")),"status"));}).findFirst();
        if(existing.isPresent()){var a=db.lock(s(existing.get(),"id"));a.put("count",((Number)a.getOrDefault("count",1)).intValue()+1);a.put("lastAt",now());a.put("temperature",value);return put(a,actor,"重复异常聚合，不重复建缺陷或工单");}
        return add("alerts",m("name",s(dev,"name")+"温度异常","code","YJ-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"deviceId",dev.get("id"),"stationId",dev.get("stationId"),"status","pending","level",p.getOrDefault("level","warning"),"temperature",value,"limit",limit,"count",1,"firstAt",now(),"lastAt",now(),"description","温度越限，需人工研判"),actor);
    }
    Optional<Map<String,Object>> shiftAt(LocalDateTime t,String station){return db.list("shifts").stream().filter(x->s(x,"stationId").equals(station)).filter(x->!s(x,"assigneeId").isBlank()&&!t.isBefore(LocalDateTime.parse(s(x,"startAt")))&&t.isBefore(LocalDateTime.parse(s(x,"endAt")))).findFirst();}
    void checkShift(Map<String,Object>d,String except){var start=LocalDateTime.parse(s(d,"startAt"));var end=LocalDateTime.parse(s(d,"endAt"));require(end.isAfter(start)&&Duration.between(start,end).toHours()<=24,"班次起止无效或超过24小时");for(var old:db.list("shifts")){if(s(old,"id").equals(except)||!s(old,"stationId").equals(s(d,"stationId")))continue;require(!start.isBefore(LocalDateTime.parse(s(old,"endAt")))||!end.isAfter(LocalDateTime.parse(s(old,"startAt"))),"与已有班次重叠，请核对后单独调整");}}
    boolean matches(Map<String,Object>r,LocalDateTime t){var date=t.toLocalDate();int week=(date.getDayOfMonth()+date.withDayOfMonth(1).getDayOfWeek().getValue()-2)/7+1;return numberFilter(r,"months",date.getMonthValue())&&numberFilter(r,"weekdays",date.getDayOfWeek().getValue())&&numberFilter(r,"days",date.getDayOfMonth())&&(ids(r,"weeks").isEmpty()||ids(r,"weeks").contains(""+week)||(ids(r,"weeks").contains("last")&&date.plusDays(7).getMonth()!=date.getMonth()))&&ids(r,"times").contains(t.toLocalTime().toString().substring(0,5));}
    boolean numberFilter(Map<String,Object>r,String key,int value){return ids(r,key).isEmpty()||ids(r,key).contains(""+value);}
    static List<String> pointDevices(Map<String,Object>p){return !ids(p,"deviceIds").isEmpty()?ids(p,"deviceIds"):s(p,"deviceId").isBlank()?List.of():List.of(s(p,"deviceId"));}
    void validateRule(String kind,Map<String,Object>r){
        need(r,"name","stationId");require(!ids(r,"times").isEmpty(),"请至少设置一个触发时间");for(String t:ids(r,"times"))LocalTime.parse(t);
        require(num(r,"completionDays")>=1&&num(r,"completionDays")<=365&&num(r,"completionDays")==Math.rint(num(r,"completionDays")),"计划完成天数须为1—365的整数");
        if(kind.equals("inspectionRules")){
            require(!items(r,"points").isEmpty(),"请设置巡检点");
            for(var p:items(r,"points")){need(p,"name");var devices=pointDevices(p);require(!devices.isEmpty(),"每个巡检点至少选择一台设备");for(String id:devices){var device=db.read(id);require(s(device,"kind").equals("devices")&&s(device,"stationId").equals(s(r,"stationId")),"巡检设备须属于本场站");}require(!items(p,"items").isEmpty(),"每个巡检点至少有一项");for(var i:items(p,"items")){need(i,"name","type");require(s(i,"deviceId").isBlank()||devices.contains(s(i,"deviceId")),"巡检项指定设备必须属于本点设备");}}
        }else require(!ids(r,"deviceIds").isEmpty(),"请设置定检设备工作项");
    }
    public List<Map<String,Object>> preview(Map<String,Object>r){var out=new ArrayList<Map<String,Object>>();var start=LocalDateTime.parse(now());for(int day=0;day<370&&out.size()<6;day++){for(String tm:ids(r,"times").stream().sorted().toList()){var t=start.toLocalDate().plusDays(day).atTime(LocalTime.parse(tm));if(t.isAfter(start)&&matches(r,t)){var sh=shiftAt(t,s(r,"stationId"));out.add(m("time",t.toString(),"assigneeId",sh.map(x->x.get("assigneeId")).orElse(""),"dueAt",t.plusDays(Integer.parseInt(s(r,"completionDays").isBlank()?"1":s(r,"completionDays"))).toString()));if(out.size()==6)break;}}}return out;}
    Map<String,Object> trigger(Map<String,Object>r,LocalDateTime t,String actor){
        require(matches(r,t),"当前时刻不满足该规则时间交集，请按触发预览推进时钟");var old=db.list("orders").stream().filter(o->s(o,"ruleId").equals(s(r,"id"))&&s(o,"plannedAt").equals(t.toString())).findFirst();if(old.isPresent())return old.get();
        var sh=shiftAt(t,s(r,"stationId"));var list=new ArrayList<Map<String,Object>>();
        if(s(r,"kind").equals("inspectionRules")){
            for(var point:items(r,"points"))for(var item:items(point,"items"))for(String device:pointDevices(point)){
                if(!s(item,"deviceId").isBlank()&&!s(item,"deviceId").equals(device))continue;
                list.add(m("id",UUID.randomUUID().toString(),"pointId",point.get("id"),"point",s(point,"name"),"deviceId",device,"name",s(item,"name"),"type",s(item,"type"),"unit",s(item,"unit"),"executed",false));
            }
        }else for(String device:ids(r,"deviceIds"))list.add(m("id",UUID.randomUUID().toString(),"deviceId",device,"name",s(r,"description"),"type","observation","executed",false));
        require(!list.isEmpty(),"规则未配置设备工作项 / 巡检项");var order=add("orders",m("name",s(r,"name"),"code","GD-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"type",s(r,"kind").equals("inspectionRules")?"inspection":"maintenance","status",sh.isPresent()?"ready":"unassigned","assigneeId",sh.map(x->x.get("assigneeId")).orElse(""),"stationId",r.get("stationId"),"deviceId",list.getFirst().get("deviceId"),"ruleId",r.get("id"),"ruleSnapshot",r,"plannedAt",t.toString(),"dueAt",t.plusDays(Integer.parseInt(s(r,"completionDays").isBlank()?"1":s(r,"completionDays"))).toString(),"requiresPermit",false,"ticketIds",List.of(),"items",list),actor);if(sh.isEmpty())message("定时任务缺班，待调度补派",s(order,"id"),"调度");return order;
    }
    @Transactional
    public Map<String,Object> tick(String time,String actor){var clock=db.lock("demo-clock");var old=LocalDateTime.parse(s(clock,"time"));var next=LocalDateTime.parse(time);require(!next.isBefore(old)&&Duration.between(old,next).toDays()<=31,"时钟只可向前推进，单次最多31天");clock.put("time",time);put(clock,actor,"推进时钟");int generated=0;
        for(String kind:List.of("maintenanceRules","inspectionRules"))for(var raw:db.list(kind)){var r=db.lock(s(raw,"id"));if(!s(r,"status").equals("active"))continue;for(var date=old.toLocalDate();!date.isAfter(next.toLocalDate());date=date.plusDays(1))for(String tm:ids(r,"times")){var t=date.atTime(LocalTime.parse(tm));if(t.isAfter(old)&&!t.isAfter(next)&&matches(r,t)){trigger(r,t,"系统定时器");generated++;}}}
        for(var raw:db.list("shifts")){if(!LocalDateTime.parse(s(raw,"endAt")).isAfter(next)&&s(raw,"status").equals("scheduled")){var sh=db.lock(s(raw,"id"));sh.put("status",s(sh,"handover").isBlank()?"handoverPending":"handedOver");put(sh,"系统","到点自动交班，交代状态独立");if(s(sh,"handover").isBlank())message("班后交代待补",s(sh,"id"),s(sh,"assigneeId"));}}
        return m("time",time,"triggered",generated);
    }
    @Transactional
    public List<Map<String,Object>> roster(Map<String,Object>p,String actor,boolean save){need(p,"startDate","endDate","stationId");var from=LocalDate.parse(s(p,"startDate"));var to=LocalDate.parse(s(p,"endDate"));require(!to.isBefore(from)&&java.time.temporal.ChronoUnit.DAYS.between(from,to)<=62,"排班范围须在63天内");var people=ids(p,"peopleIds");var slots=items(p,"slots");require(!people.isEmpty()&&!slots.isEmpty(),"请按顺序选择负责人并配置班次");var out=new ArrayList<Map<String,Object>>();int index=0;for(var date=from;!date.isAfter(to);date=date.plusDays(1))for(var slot:slots){var start=date.atTime(LocalTime.parse(s(slot,"start")));var end=date.plusDays(yes(slot,"nextDay")?1:0).atTime(LocalTime.parse(s(slot,"end")));var d=m("name",s(slot,"name"),"stationId",p.get("stationId"),"startAt",start.toString(),"endAt",end.toString(),"assigneeId",people.get(index++%people.size()),"chiefId",p.getOrDefault("chiefId","person-chief"),"status","scheduled");checkShift(d,"");for(var prior:out)require(!start.isBefore(LocalDateTime.parse(s(prior,"endAt")))||!end.isAfter(LocalDateTime.parse(s(prior,"startAt"))),"批量班次内部存在重叠");out.add(d);}
        if(save){var results=new ArrayList<Map<String,Object>>();for(var d:out)results.add(add("shifts",d,actor));return results;}return out;
    }
    @Transactional
    public List<Map<String,Object>> leave(Map<String,Object>p,String actor,boolean save){need(p,"shiftId","endAt");var first=db.lock(s(p,"shiftId"));require(LocalDateTime.parse(s(first,"startAt")).isAfter(LocalDateTime.parse(now())),"只能调整尚未开始的班次");var shifts=db.list("shifts").stream().filter(x->s(x,"stationId").equals(s(first,"stationId"))&&s(x,"startAt").compareTo(s(first,"startAt"))>=0&&s(x,"startAt").compareTo(s(p,"endAt"))<=0).sorted(Comparator.comparing(x->s(x,"startAt"))).collect(Collectors.toCollection(ArrayList::new));require(shifts.size()>1,"请选择包含后续班次的调班范围");String absent=s(first,"assigneeId");var out=new ArrayList<Map<String,Object>>();for(int i=0;i<shifts.size();i++){var sh=save?db.lock(s(shifts.get(i),"id")):new LinkedHashMap<>(shifts.get(i));sh.put("previousAssignee",sh.get("assigneeId"));sh.put("assigneeId",i+1<shifts.size()?shifts.get(i+1).get("assigneeId"):absent);out.add(save?put(sh,actor,"休假调班顺延"):sh);}return out;}
}
