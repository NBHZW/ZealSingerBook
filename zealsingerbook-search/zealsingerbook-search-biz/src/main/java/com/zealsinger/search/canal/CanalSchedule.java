package com.zealsinger.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import com.zealsinger.book.framework.common.enums.StatusEnum;
import com.zealsinger.search.config.CanalProperties;
import com.zealsinger.search.domain.enums.NoteStatusEnum;
import com.zealsinger.search.domain.enums.NoteVisibleEnum;
import com.zealsinger.search.domain.index.NoteIndex;
import com.zealsinger.search.domain.index.UserIndex;
import com.zealsinger.search.mapper.SelectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CanalSchedule implements Runnable {
    @Resource
    private CanalConnector canalConnector;
    @Resource
    private CanalProperties canalProperties;

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private SelectMapper selectMapper;

    private final  String USER_TABLE_NAME = "t_user";

    private final  String NOTE_TABLE_NAME = "t_note";


    @Override
    @Scheduled(fixedDelay = 100)  // 每隔100ms执行一次
    public void run() {
        // 批次ID 初始化为-1 标识没开始或者未获取到数据
        long batchId = -1;
        try{
            // canal批量拉取消息 返回的数据量由batchSize控制 如果不足则会直接拉取已有的数据
            Message message= canalConnector.getWithoutAck(canalProperties.getBatchSize());
            // 获取当前拉取批次的ID
            batchId = message.getId();
            // 拉取数量
            List<CanalEntry.Entry> entryList = message.getEntries();
            int messageSize = entryList.size();
            if(batchId==-1 || messageSize==0 ){
                try{
                    // 没有拉取到数据 则直接睡眠1s 防止频繁拉取
                    TimeUnit.SECONDS.sleep(1);
                }catch (InterruptedException e){}
            }else{
                printEntry(entryList);
            }
            // 对当前消息进行ACK确认  标识该批次成功被消费
            canalConnector.ack(batchId);
        }catch (Exception e){
            log.error("消费 Canal 批次数据异常", e);
            // 如果出现异常，需要进行数据回滚，以便重新消费这批次的数据
            canalConnector.rollback(batchId);
        }
    }

    /**
     * 打印这一批次中的数据条目（和官方示例代码一致，后续小节中会自定义这块）
     * @param entrys
     */
    private void printEntry(List<CanalEntry.Entry> entrys) throws Exception {
        for (CanalEntry.Entry entry : entrys) {
            // 只关注行数据 不关注其他的事务和类型
            if(entry.getEntryType() == CanalEntry.EntryType.ROWDATA){
                // 获取行数据的事件类型
                CanalEntry.EventType eventType = entry.getHeader().getEventType();
                // 获取发生变化的数据库库名称
                String database = entry.getHeader().getSchemaName();
                // 获取表名
                String table = entry.getHeader().getTableName();

                // 解析出RowChange对象  该对象中包含了RowData行数据和事件相关的信息
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                // 遍历获取所有的行数据
                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    // 每行数据中所有列的最新数值
                    List<CanalEntry.Column> afterColumnsList = rowData.getAfterColumnsList();
                    // 解析为Map方便后续操作
                    // 将列数据解析为 Map，方便后续处理
                    Map<String, Object> columnMap = parseColumns2Map(afterColumnsList);

                    log.info("EventType: {}, Database: {}, Table: {}, Columns: {}", eventType, database, table, columnMap);

                    // TODO：处理事件任务
                    processEvent(columnMap,table,eventType);
                }
            }
        }
    }

    /**
     * 处理事件
     * @param columnMap
     * @param table
     * @param eventType
     */
    private void processEvent(Map<String, Object> columnMap, String table, CanalEntry.EventType eventType) throws Exception {
        switch (table){
            case USER_TABLE_NAME -> handleNoteEvent(columnMap,eventType);
            case NOTE_TABLE_NAME -> handleUserEvent(columnMap,eventType);
            default -> log.warn("Table: {} not support", table);
        }
    }

    /**
     * 处理用户表事件
     * @param columnMap
     * @param eventType
     */
    private void handleUserEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws Exception {
        // 获取用户 ID
        Long userId = Long.parseLong(columnMap.get("id").toString());

        // 不同的事件，处理逻辑不同
        switch (eventType) {
            case INSERT -> syncUserIndex(userId); // 记录新增事件
            case UPDATE -> { // 记录更新事件
                // 用户变更后的状态
                Integer status = Integer.parseInt(columnMap.get("status").toString());
                // 逻辑删除
                Integer isDeleted = Integer.parseInt(columnMap.get("is_deleted").toString());

                if (Objects.equals(status, StatusEnum.ENABLE.getValue())
                        && Objects.equals(isDeleted, 0)) { // 用户状态为已启用，并且未被逻辑删除，将状态重新更新上去即可
                    // 更新用户索引、笔记索引
                    syncNotesIndexAndUserIndex(userId);
                } else if (Objects.equals(status, StatusEnum.DISABLED.getValue()) // 用户状态为禁用  zha
                        || Objects.equals(isDeleted, 1)) { // 被逻辑删除
                    // TODO: 删除用户文档
                    deleteUserDocument(String.valueOf(userId));
                }
            }
            default -> log.warn("Unhandled event type for t_user: {}", eventType);
        }
    }
    /**
     * 删除指定 ID 的用户文档
     * @param documentId
     * @throws Exception
     */
    private void deleteUserDocument(String documentId) throws Exception {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(UserIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /**
     * 同步用户索引、笔记索引（可能是多条）
     * @param userId
     */
    private void syncNotesIndexAndUserIndex(Long userId) throws Exception {
        // TODO
        // 创建一个 BulkRequest
        BulkRequest bulkRequest = new BulkRequest();

        // 1. 用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);

        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : userResult) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将每个 IndexRequest 加入到 BulkRequest
            bulkRequest.add(indexRequest);
        }

        // 2. 笔记索引
        List<Map<String, Object>> noteResult = selectMapper.selectEsNoteIndexData(null, userId);
        for (Map<String, Object> recordMap : noteResult) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将每个 IndexRequest 加入到 BulkRequest
            bulkRequest.add(indexRequest);
        }

        // 执行批量请求
        restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
    }

    /**
     * 同步用户索引
     * @param userId
     */
    private void syncUserIndex(Long userId) throws Exception {
        // 1. 同步用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);

        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : userResult) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将数据写入 Elasticsearch 索引
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 处理笔记表事件
     * @param columnMap
     * @param eventType
     */
    private void handleNoteEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws Exception {
        long noteId = Long.parseLong(columnMap.get("id").toString());
        // 不同的事件，处理逻辑不同
        switch (eventType){
            // 处理新增事件
            case INSERT -> syncNoteIndex(noteId);
            // 处理更新事件
            case UPDATE -> {
                // 笔记变更后的状态
                Integer status = Integer.parseInt(columnMap.get("status").toString());
                // 笔记可见范围
                Integer visible = Integer.parseInt(columnMap.get("visible").toString());
                if(Objects.equals(status, NoteStatusEnum.NORMAL.getCode())){
                    if(Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode())){
                        // 正常且公开的数据我们才放入到ES中
                        syncNoteIndex(noteId);
                    }
                }else if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode()) // 仅对自己可见
                        || Objects.equals(status, NoteStatusEnum.DELETED.getCode())
                        || Objects.equals(status, NoteStatusEnum.DOWNED.getCode())) { // 被逻辑删除、被下架
                    // TODO: 删除笔记文档
                    deleteNoteDocument(String.valueOf(noteId));
                }
            }
            // 其余事件  从业务上而言我们的是逻辑删除 实际上的对应的操作是MySQL中更新操作 所以不需要单独的处理
            default -> log.warn("Unhandled event type for t_note: {}", eventType);
        }
    }

    /**
     * 笔记删除删除
     */
    private void deleteNoteDocument(String documentId) throws Exception {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(NoteIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /**
     * 笔记新增事件
     * @param noteId
     */
    private void syncNoteIndex(long noteId) throws IOException {
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId,null);
        for(Map<String, Object> recordMap : result){
            // 创建索引请求对象
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档ID
            indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
            // 设置文档内容
            indexRequest.source(recordMap);
            //发请求写入ES
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 将行数据对象转化为Map方便后续处理key为列字段 value为对应的值
     * @param columns
     * @return
     */
    private Map<String, Object> parseColumns2Map(List<CanalEntry.Column> columns) {
        Map<String,Object> map = new HashMap<>();
        columns.forEach(column -> {
            if(Objects.isNull(column)) return;
            map.put(column.getName(), column.getValue());
        });
        return map;
    }
}
