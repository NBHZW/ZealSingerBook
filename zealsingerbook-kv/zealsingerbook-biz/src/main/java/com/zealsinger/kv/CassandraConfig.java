package com.zealsinger.kv;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;

/**
 * extends AbstractCassandraConfiguration: AbstractCassandraConfiguration 是 Spring Data Cassandra 提供的一个抽象基类，它包含了一些默认的方法实现，用于配置 Cassandra 连接。
 * getKeyspaceName(), getContactPoints(), 和 getPort() 方法:
 * 这些方法都是覆盖（override）自父类 AbstractCassandraConfiguration 的抽象方法。它们分别返回 keyspace 名称、连接和端口号。
 * 当 Spring 初始化 Cassandra 连接时，会调用这些方法来获取配置信息。
 */
@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keySpace;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    /*
     * Provide a keyspace name to the configuration.
     */
    @Override
    public String getKeyspaceName() {
        return keySpace;
    }

    @Override
    public String getContactPoints() {
        return contactPoints;
    }

    @Override
    public int getPort() {
        return port;
    }
}
