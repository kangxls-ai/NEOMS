package com.neoms;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class Store {
    final JdbcTemplate jdbc; final ObjectMapper json;
    static final Set<String> KINDS=Set.of("stations","devices","people","teams","shifts","notes","maintenanceRules","inspectionRules","alerts","defects","orders","workTickets","operationTickets","notices","commands","workHazards","operationHazards","duties","messages","settings");
    Store(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public JdbcTemplate jdbc(){return jdbc;}
    void schema(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS neoms_records (id VARCHAR(80) PRIMARY KEY, kind VARCHAR(40) NOT NULL, payload LONGTEXT NOT NULL, revision INT NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS neoms_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, record_id VARCHAR(80) NOT NULL, kind VARCHAR(40) NOT NULL, actor VARCHAR(100) NOT NULL, action_name VARCHAR(100) NOT NULL, happened_at VARCHAR(40) NOT NULL, snapshot LONGTEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS neoms_users (id VARCHAR(80) PRIMARY KEY, username VARCHAR(60) NOT NULL UNIQUE, display_name VARCHAR(100) NOT NULL, role_name VARCHAR(80) NOT NULL, password_hash VARCHAR(100) NOT NULL)");
    }
    public Map<String,Object> read(String id){return fetch(id,false);}
    Map<String,Object> lock(String id){return fetch(id,true);}
    private Map<String,Object> fetch(String id,boolean lock){var rows=jdbc.queryForList("SELECT payload,revision FROM neoms_records WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new IllegalArgumentException("记录不存在");var data=parse(rows.getFirst().get("payload").toString());data.put("_revision",rows.getFirst().get("revision"));return data;}
    public List<Map<String,Object>> list(String kind){checkKind(kind);return jdbc.query("SELECT payload,revision FROM neoms_records WHERE kind=? ORDER BY id",(rs,n)->{var d=parse(rs.getString(1));d.put("_revision",rs.getInt(2));return d;},kind);}
    void checkKind(String k){if(!KINDS.contains(k))throw new IllegalArgumentException("未知业务类型");}
    Map<String,Object> insert(String kind,Map<String,Object> data){checkKind(kind);var d=new LinkedHashMap<>(data);d.remove("_revision");d.putIfAbsent("id",kind+"-"+UUID.randomUUID().toString().substring(0,12));d.put("kind",kind);jdbc.update("INSERT INTO neoms_records(id,kind,payload,revision) VALUES(?,?,?,1)",d.get("id"),kind,stringify(d));d.put("_revision",1);return d;}
    Map<String,Object> save(Map<String,Object> d){var copy=new LinkedHashMap<>(d);int rev=((Number)copy.remove("_revision")).intValue();int count=jdbc.update("UPDATE neoms_records SET payload=?,revision=revision+1 WHERE id=? AND revision=?",stringify(copy),copy.get("id"),rev);if(count!=1)throw new IllegalStateException("记录已被他人更新，请刷新后重试");copy.put("_revision",rev+1);return copy;}
    void history(Map<String,Object> d,String actor,String action,String time){jdbc.update("INSERT INTO neoms_history(record_id,kind,actor,action_name,happened_at,snapshot) VALUES(?,?,?,?,?,?)",d.get("id"),d.get("kind"),actor,action,time,stringify(d));}
    List<Map<String,Object>> history(String id){return jdbc.queryForList("SELECT id,actor,action_name AS action,happened_at AS time,snapshot FROM neoms_history WHERE record_id=? ORDER BY id DESC",id);}
    String stringify(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("数据无法序列化");}}
    Map<String,Object> parse(String s){try{return json.readValue(s,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("记录格式错误");}}
}
