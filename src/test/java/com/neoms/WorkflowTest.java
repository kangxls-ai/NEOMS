package com.neoms;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static com.neoms.Engine.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.datasource.url=jdbc:h2:mem:neoms;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@Transactional
class WorkflowTest {
 @Autowired Engine engine; @Autowired Store db;
 final String actor="person-field";
 Map<String,Object> act(String id,String action,Map<String,Object> p){if(action.equals("approve"))p.putIfAbsent("checked",true);if(action.equals("terminate")){p.putIfAbsent("workEndAt",engine.now());p.putIfAbsent("ticketEndAt",engine.now());p.putIfAbsent("siteRestored",true);p.putIfAbsent("personnelWithdrawn",true);p.putIfAbsent("guardianId",actor);p.putIfAbsent("riskAcknowledged",true);}return engine.act(id,action,p,actor);}
 Map<String,Object> ticket(String kind,String subtype,List<String> orders){return engine.create(kind,m("name","测试票据","subtype",subtype,"firstType","集电线路操作票","secondType","运行转检修","issuerId","person-signer","supervisorId","person-permit","deviceName","01号逆变器","description","演示检修","leaderId",actor,"stationId","station-1","plannedStart",engine.now(),"plannedEnd","2026-11-02T18:00:00","orderIds",orders,"hazards",List.of(m("id","h1","analysis","测试风险","measures","测试预控")),"measures",List.of(m("id","m1","name","隔离确认","executed",false)),"items",List.of(m("id","i1","name","确认设备","executed",false))),actor);}
 @Test void completeAlarmToAcceptanceWithTicketGates(){
  var a=act("device-1","sample",m("temperature",84));String aid=s(a,"id");
  act("device-1","sample",m("temperature",85));assertEquals(2,db.read(aid).get("count"));
  String fid=s(act(aid,"confirm",m("reason","风扇异响与温升一致")),"defectId");
  assertEquals("pendingApproval",s(db.read(fid),"status"));
  String oid=s(act(fid,"approve",m()),"orderId");
  assertThrows(IllegalArgumentException.class,()->act(fid,"approve",m()));
  act(oid,"dispatch",m("assigneeId",actor,"dueAt","2026-11-02T18:00:00"));act(oid,"accept",m());
  assertThrows(IllegalArgumentException.class,()->act(oid,"start",m()));
  String tid=s(ticket("workTickets","electrical1",List.of(oid)),"id");
  act(tid,"submit",m());act(tid,"approve",m());act(tid,"approve",m());
  assertThrows(IllegalArgumentException.class,()->act(oid,"start",m()));
  act(tid,"approve",m());act(oid,"start",m());
  act(oid,"record",m("rootCause","风扇损坏","work","更换并复测","recoveryTemp",52,"recoveryPower",905,"observation",30,"evidence",List.of(m("type","measurement","name","模拟复测数据"))));
  act(oid,"submit",m());
  assertThrows(IllegalArgumentException.class,()->act(oid,"approve",m("checked",true,"conclusion","复核通过")));
  act(tid,"step",m("itemId","m1"));db.jdbc().update("INSERT INTO neoms_files VALUES('test-file','test.txt','text/plain','test.txt')");
  act(tid,"terminate",m("description","现场恢复","attachmentIds",List.of("test-file")));
  act(oid,"reject",m("reason","补充说明"));assertEquals("working",s(db.read(oid),"status"));
  act(oid,"submit",m());act(oid,"approve",m("checked",true,"conclusion","复核通过"));
  assertEquals("closed",s(db.read(oid),"status"));assertEquals("repaired",s(db.read(fid),"status"));
  assertTrue(db.history(tid).size()>=7);
 }
 @Test void returnedTicketRestartsAndOperationRequiresOnlySign(){
  String t=s(ticket("workTickets","wind",List.of()),"id");act(t,"submit",m());act(t,"approve",m());act(t,"return",m("reason","措施补充"));
  assertFalse(db.read(t).containsKey("signedAt"));act(t,"submit",m());assertEquals(2,db.read(t).get("revision"));assertEquals("pendingSign",s(db.read(t),"status"));
  String o=s(ticket("operationTickets","breaker",List.of()),"id");act(o,"submit",m());assertEquals("executing",s(act(o,"approve",m()),"status"));
  assertThrows(IllegalArgumentException.class,()->act(o,"terminate",m("description","结束","attachmentIds",List.of("missing"))));
 }
 @Test void ruleClockDedupAndMissingShift(){
  engine.tick("2026-11-02T10:00:00",actor);int count=db.list("orders").size();
  act("mr-1","trigger",m());assertEquals(count,db.list("orders").size());
  var generated=db.list("orders").stream().filter(x->s(x,"ruleId").equals("mr-1")&&s(x,"plannedAt").equals("2026-11-02T10:00")).findFirst().orElseThrow();
  assertEquals("ready",s(generated,"status"));
  engine.tick("2026-11-04T10:00:00",actor);
  assertTrue(db.list("orders").stream().anyMatch(x->s(x,"status").equals("unassigned")&&s(x,"plannedAt").startsWith("2026-11-04")));
  assertTrue(db.list("shifts").stream().anyMatch(x->s(x,"status").equals("handoverPending")));
 }
 @Test void shiftsResolveWithinStationAndRejectOverlap(){
  engine.create("shifts",m("name","另一场站","stationId","station-2","assigneeId","person-chief","startAt","2026-11-02T08:00:00","endAt","2026-11-02T20:00:00"),actor);
  assertEquals("person-chief",engine.shiftAt(LocalDateTime.parse(engine.now()),"station-2").orElseThrow().get("assigneeId"));
  assertThrows(IllegalArgumentException.class,()->engine.create("shifts",m("stationId","station-2","assigneeId",actor,"startAt","2026-11-02T09:00:00","endAt","2026-11-02T21:00:00"),actor));
 }
 @Test void multipleDevicesAtPointAndSpecificItems(){
  var rule=engine.create("inspectionRules",m("name","多设备线路","stationId","station-1","completionDays",1,"times",List.of("10:00"),"points",List.of(m("id","p1","name","逆变器阵区","deviceIds",List.of("device-1","device-2"),"items",List.of(m("name","温度","type","record"),m("name","专用项","type","observation","deviceId","device-2"))))),actor);
  engine.tick("2026-11-02T10:00:00",actor);var order=db.list("orders").stream().filter(x->s(x,"ruleId").equals(s(rule,"id"))).findFirst().orElseThrow();
  assertEquals(3,items(order,"items").size());assertEquals(2,items(order,"items").stream().filter(x->s(x,"deviceId").equals("device-2")).count());
 }
 @Test void contactRequiresHazardExecutionAndProcessHasTypedFields(){
  String id=s(ticket("workTickets","contact",List.of()),"id");act(id,"submit",m());act(id,"approve",m());
  db.jdbc().update("INSERT INTO neoms_files VALUES('contact-file','test.txt','text/plain','test.txt')");
  assertThrows(IllegalArgumentException.class,()->act(id,"terminate",m("description","现场恢复","attachmentIds",List.of("contact-file"))));
  act(id,"hazard",m("itemId","h1"));assertEquals("terminated",s(act(id,"terminate",m("description","现场恢复","attachmentIds",List.of("contact-file"))),"status"));
  String other=s(ticket("workTickets","electrical1",List.of()),"id");act(other,"submit",m());act(other,"approve",m());act(other,"approve",m());act(other,"approve",m());
  assertThrows(IllegalArgumentException.class,()->act(other,"process",m("processType","工作负责人变更","description","交接")));
  var result=act(other,"process",m("processType","工作负责人变更","description","交接","eventAt",engine.now(),"oldLeaderId",actor,"newLeaderId","person-permit","approvedBy","person-signer","approvalAt",engine.now()));
  assertEquals("person-permit",items(result,"processRecords").getFirst().get("newLeaderId"));
 }
 @Test void invalidOperationSubtypeAndUncheckedPermitRejected(){
  var bad=ticket("operationTickets","operation",List.of());bad.put("firstType","PT操作票");bad.put("secondType","运行转冷备用");engine.edit(s(bad,"id"),bad,actor);
  assertThrows(IllegalArgumentException.class,()->act(s(bad,"id"),"submit",m()));
  String id=s(ticket("workTickets","electrical1",List.of()),"id");act(id,"submit",m());act(id,"approve",m());
  assertThrows(IllegalArgumentException.class,()->act(id,"approve",m("checked",false)));
 }
 @Test void cosmeticMigrationPreservesBusinessAndCredentials(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp)throws Exception{
  var order=db.read("order-6");order.put("name","演示检修任务");db.save(order);
  db.jdbc().update("UPDATE neoms_users SET display_name='演示管理员' WHERE id='person-admin'");
  String password=db.jdbc().queryForObject("SELECT password_hash FROM neoms_users WHERE id='person-admin'",String.class);
  db.history(db.read("order-6"),"演示管理员","演示处理记录",engine.now());
  java.nio.file.Files.writeString(temp.resolve("known.txt"),"NEOMS 虚构演示票据记录");
  db.jdbc().update("INSERT INTO neoms_files VALUES('migration-file','演示票据终结记录.txt','text/plain','known.txt')");
  LabelMigration.run(db,temp);
  assertEquals("检修任务",db.read("order-6").get("name"));assertEquals(order.get("status"),db.read("order-6").get("status"));assertEquals(order.get("ticketIds"),db.read("order-6").get("ticketIds"));
  assertEquals("管理员",db.jdbc().queryForObject("SELECT display_name FROM neoms_users WHERE id='person-admin'",String.class));assertEquals(password,db.jdbc().queryForObject("SELECT password_hash FROM neoms_users WHERE id='person-admin'",String.class));
  assertFalse(db.history("order-6").getFirst().get("snapshot").toString().contains("演示"));assertFalse(java.nio.file.Files.readString(temp.resolve("known.txt")).contains("演示"));
  int revision=((Number)db.read("order-6").get("_revision")).intValue();LabelMigration.run(db,temp);assertEquals(revision,db.read("order-6").get("_revision"));
 }
 @Test void materialAmountAndPauseKeepDeadline(){
  var before=db.read("order-6");String due=s(before,"dueAt");act("order-6","pause",m("reason","待备件"));assertEquals(due,s(db.read("order-6"),"dueAt"));
  var d=act("order-6","material",m("name","风扇","code","F1","unit","台","date","2026-11-02","quantity",2,"price",123.45));
  assertEquals(246.9,items(d,"materials").getLast().get("total"));
 }
}
