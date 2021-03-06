package com.a.eye.skywalking.reciever.processor;

import com.a.eye.skywalking.reciever.processor.exception.HBaseInitFailedException;
import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.reciever.conf.Config;
import com.a.eye.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.a.eye.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

import static org.apache.hadoop.hbase.util.Bytes.toBytes;

public abstract class AbstractSpanProcessor implements IProcessor {
    private static Logger        logger        = LogManager.getLogger(AbstractSpanProcessor.class);
    private static Configuration configuration = null;
    private static Connection connection;

    static {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            if (Config.HBaseConfig.ZK_HOSTNAME == null || "".equals(Config.HBaseConfig.ZK_HOSTNAME)) {
                logger.error("Miss HBase ZK quorum Configuration", new IllegalArgumentException("Miss HBase ZK quorum Configuration"));
                System.exit(-1);
            }
            configuration.set("hbase.zookeeper.quorum", Config.HBaseConfig.ZK_HOSTNAME);
            configuration.set("hbase.zookeeper.property.clientPort", Config.HBaseConfig.CLIENT_PORT);
        }

        try {
            connection = ConnectionFactory.createConnection(configuration);
            Admin admin = connection.getAdmin();
            if (!admin.tableExists(TableName.valueOf(Config.HBaseConfig.TABLE_NAME))){
                HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf(Config.HBaseConfig.TABLE_NAME));
                HColumnDescriptor family = new HColumnDescriptor(toBytes(Config.HBaseConfig.FAMILY_COLUMN_NAME));
                descriptor.addFamily(family);
                admin.createTable(descriptor);
            }
        } catch (IOException e) {
            ServerHealthCollector.getCurrentHeathReading("hbase").updateData(ServerHeathReading.ERROR, "connect to hbase failure.");
            throw new HBaseInitFailedException("initHBaseClient failure", e);
        }


    }

    @Override
    public void process(List<AbstractDataSerializable> serializedObjects) {
        doAlarm(serializedObjects);
        doSaveHBase(connection, serializedObjects);
    }

    public abstract void doAlarm(List<AbstractDataSerializable> serializedObjects);

    public abstract void doSaveHBase(Connection connection, List<AbstractDataSerializable> serializedObjects);

}
