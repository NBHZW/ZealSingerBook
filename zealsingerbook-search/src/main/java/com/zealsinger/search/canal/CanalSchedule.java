package com.zealsinger.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.zealsinger.search.config.CanalProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CanalSchedule implements Runnable {
    @Resource
    private CanalConnector canalConnector;
    @Resource
    private CanalProperties canalProperties;


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
    private void printEntry(List<CanalEntry.Entry> entrys) {
        for (CanalEntry.Entry entry : entrys) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN
                    || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            CanalEntry.RowChange rowChage = null;
            try {
                rowChage = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("ERROR ## parser of eromanga-event has an error , data:" + entry.toString(),
                        e);
            }

            CanalEntry.EventType eventType = rowChage.getEventType();
            System.out.println(String.format("================> binlog[%s:%s] , name[%s,%s] , eventType : %s",
                    entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                    entry.getHeader().getSchemaName(), entry.getHeader().getTableName(),
                    eventType));

            for (CanalEntry.RowData rowData : rowChage.getRowDatasList()) {
                if (eventType == CanalEntry.EventType.DELETE) {
                    printColumn(rowData.getBeforeColumnsList());
                } else if (eventType == CanalEntry.EventType.INSERT) {
                    printColumn(rowData.getAfterColumnsList());
                } else {
                    System.out.println("-------> before");
                    printColumn(rowData.getBeforeColumnsList());
                    System.out.println("-------> after");
                    printColumn(rowData.getAfterColumnsList());
                }
            }
        }


    }

    /**
     * 打印字段信息
     * @param columns
     */
    private static void printColumn(List<CanalEntry.Column> columns) {
        for (CanalEntry.Column column : columns) {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
        }
    }
}
