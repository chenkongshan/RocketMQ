package com.alibaba.rocketmq.client.producer;

import com.alibaba.rocketmq.client.exception.MQBrokerException;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.remoting.exception.RemotingException;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Company: PAJK
 * Author: chenkongshan
 * Created: 2017/5/12
 * Version: since
 */
public class ProduceMsgTest {

    private static String[] lowerChars;
    private static String[] upperChars;
    private static String[] nums;
    private static String[][] total;

    static {
        int lowerLen = 122 - 97 + 1;
        lowerChars = new String[lowerLen];
        for (int i = 0; i < lowerLen; i++) {
            lowerChars[i] = String.valueOf((char) (97 + i));
        }

        upperChars = new String[90 - 65 + 1];
        for (int i = 0; i < upperChars.length; i++) {
            upperChars[i] = String.valueOf((char) (65 + i));
        }

        nums = new String[10];
        for (int i = 0; i < 10; i++) {
            nums[i] = i + "";
        }

        total = new String[][]{lowerChars, upperChars, nums};
    }

    private static String randomString(int length) {
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(3);
            String[] ss = total[index];
            index = random.nextInt(ss.length);
            result.append(ss[index]);
        }
        return result.toString();
    }

    @Test
    public void sendMsg() throws Exception {
        final DefaultMQProducer producer = new DefaultMQProducer("producer");
        producer.setNamesrvAddr("127.0.0.1:9876");
        producer.start();

        final AtomicInteger count = new AtomicInteger(1);

        ExecutorService service = Executors.newFixedThreadPool(8, new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "tag_" + count.getAndIncrement());
            }
        });
        for (int i = 0; i < 8; i++) {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.interrupted()) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(20);
                            Message message = new Message("test", Thread.currentThread().getName(), randomString(1000).getBytes("utf-8"));
                            SendResult send = producer.send(message);
                            System.out.println(send);
                        } catch (InterruptedException e) {
                            System.out.println(e.getMessage());
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        } catch (MQClientException e) {
                            e.printStackTrace();
                        } catch (RemotingException e) {
                            e.printStackTrace();
                        } catch (MQBrokerException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
        TimeUnit.SECONDS.sleep(60 * 10);
        service.shutdownNow();

        producer.shutdown();
        System.exit(0);
    }
}
