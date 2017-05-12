package com.alibaba.rocketmq.client.producer;

import com.alibaba.rocketmq.common.message.Message;
import org.junit.Test;

/**
 * Company: PAJK
 * Author: chenkongshan
 * Created: 2017/5/12
 * Version: since
 */
public class ProduceMsgTest {

    @Test
    public void sendMsg() throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("producer");
        producer.setNamesrvAddr("127.0.0.1:9876");
        producer.start();

        Message message = new Message("test", "test", "this is a test msg".getBytes("utf-8"));
        SendResult send = producer.send(message);
        System.out.println(send);

        producer.shutdown();
    }
}
