package com.alibaba.rocketmq.client.consumer.recmsg;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.message.MessageExt;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Company: PAJK
 * Author: chenkongshan
 * Created: 2017/5/12
 * Version: since
 */
public class ConsumTest {

    @Test
    public void testConsume() throws MQClientException, InterruptedException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testconsumer");
        consumer.setNamesrvAddr("127.0.0.1:9876");
        consumer.subscribe("test", "test");
        consumer.registerMessageListener(new MsgListener());
        consumer.start();
        System.out.println("消费者启动成功");
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }

    private static class MsgListener implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            for (MessageExt ext : msgs) {
                System.out.println(Thread.currentThread().getName() + ":" + ext);
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }
}
