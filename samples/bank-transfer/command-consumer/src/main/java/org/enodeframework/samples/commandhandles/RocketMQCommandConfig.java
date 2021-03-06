package org.enodeframework.samples.commandhandles;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.enodeframework.rocketmq.message.RocketMQCommandListener;
import org.enodeframework.samples.QueueProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class RocketMQCommandConfig {

    @Value("${spring.enode.mq.topic.command}")
    private String commandTopic;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer defaultMQPushConsumer(RocketMQCommandListener commandListener) throws MQClientException {
        DefaultMQPushConsumer defaultMQPushConsumer = new DefaultMQPushConsumer();
        defaultMQPushConsumer.setConsumerGroup(QueueProperties.DEFAULT_CONSUMER_GROUP3);
        defaultMQPushConsumer.setNamesrvAddr(QueueProperties.NAMESRVADDR);
        defaultMQPushConsumer.subscribe(commandTopic, "*");
        defaultMQPushConsumer.setMessageListener(commandListener);
        return defaultMQPushConsumer;
    }

    @Bean(name = "enodeMQProducer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer defaultMQProducer() {
        DefaultMQProducer producer = new DefaultMQProducer();
        producer.setNamesrvAddr(QueueProperties.NAMESRVADDR);
        producer.setProducerGroup(QueueProperties.DEFAULT_PRODUCER_GROUP0);
        return producer;
    }
}
