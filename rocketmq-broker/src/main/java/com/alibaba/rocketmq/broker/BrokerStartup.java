/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.broker;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.alibaba.rocketmq.common.BrokerConfig;
import com.alibaba.rocketmq.common.MQVersion;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.constant.LoggerName;
import com.alibaba.rocketmq.remoting.common.RemotingUtil;
import com.alibaba.rocketmq.remoting.netty.NettyClientConfig;
import com.alibaba.rocketmq.remoting.netty.NettyServerConfig;
import com.alibaba.rocketmq.remoting.netty.NettySystemConfig;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;
import com.alibaba.rocketmq.srvutil.ServerUtil;
import com.alibaba.rocketmq.store.config.BrokerRole;
import com.alibaba.rocketmq.store.config.MessageStoreConfig;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author shijia.wxr
 */
public class BrokerStartup {
    public static Properties properties = null;
    public static CommandLine commandLine = null;
    public static String configFile = null;
    public static Logger log;

    public static void main(String[] args) {
        List<String> list = new ArrayList<String>();
        list.add("-n");
        list.add("127.0.0.1:9876");
        args = list.toArray(new String[0]);

        //set rocketmq home
        System.setProperty(MixAll.ROCKETMQ_HOME_PROPERTY, System.getProperty("user.dir"));
        start(createBrokerController(args));
    }

    public static BrokerController start(BrokerController controller) {
        try {

            controller.start();
            String tip =
                    "The broker[" + controller.getBrokerConfig().getBrokerName() + ", "
                            + controller.getBrokerAddr() + "] boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();

            if (null != controller.getBrokerConfig().getNamesrvAddr()) {
                tip += " and name server is " + controller.getBrokerConfig().getNamesrvAddr();
            }

            log.info(tip);
            System.out.println(tip);

            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    public static BrokerController createBrokerController(String[] args) {
        System.setProperty(RemotingCommand.RemotingVersionKey, Integer.toString(MQVersion.CurrentVersion));


        if (null == System.getProperty(NettySystemConfig.SystemPropertySocketSndbufSize)) {
            NettySystemConfig.socketSndbufSize = 131072;
        }


        if (null == System.getProperty(NettySystemConfig.SystemPropertySocketRcvbufSize)) {
            NettySystemConfig.socketRcvbufSize = 131072;
        }

        try {

            //PackageConflictDetect.detectFastjson();

            //添加h“help”和n“namesrvAddr”的option
            Options options = ServerUtil.buildCommandlineOptions(new Options());
            commandLine =
                    ServerUtil.parseCmdLine("mqbroker", args, buildCommandlineOptions(options),
                            new PosixParser());
            if (null == commandLine) {
                System.exit(-1);
                return null;
            }


            final BrokerConfig brokerConfig = new BrokerConfig();
            final NettyServerConfig nettyServerConfig = new NettyServerConfig();
            final NettyClientConfig nettyClientConfig = new NettyClientConfig();
            nettyServerConfig.setListenPort(10911);
            //消息存储服务配置项
            final MessageStoreConfig messageStoreConfig = new MessageStoreConfig();

            //从broker配置，暂时不看
            if (BrokerRole.SLAVE == messageStoreConfig.getBrokerRole()) {
                int ratio = messageStoreConfig.getAccessMessageInMemoryMaxRatio() - 10;
                messageStoreConfig.setAccessMessageInMemoryMaxRatio(ratio);
            }

            //打印配置项信息
            if (commandLine.hasOption('p')) {
                MixAll.printObjectProperties(null, brokerConfig);
                MixAll.printObjectProperties(null, nettyServerConfig);
                MixAll.printObjectProperties(null, nettyClientConfig);
                MixAll.printObjectProperties(null, messageStoreConfig);
                System.exit(0);
            } else if (commandLine.hasOption('m')) {  //只打印重要配置项信息ImportantField
                MixAll.printObjectProperties(null, brokerConfig, true);
                MixAll.printObjectProperties(null, nettyServerConfig, true);
                MixAll.printObjectProperties(null, nettyClientConfig, true);
                MixAll.printObjectProperties(null, messageStoreConfig, true);
                System.exit(0);
            }

            //读取配置项，因此可以通过配置文件改变配置项brokerConfig,nettyServerConfig,nettyClientConfig,messageStoreConfig
            if (commandLine.hasOption('c')) {
                String file = commandLine.getOptionValue('c');
                if (file != null) {
                    configFile = file;
                    InputStream in = new BufferedInputStream(new FileInputStream(file));
                    properties = new Properties();
                    properties.load(in);

                    parsePropertie2SystemEnv(properties);
                    MixAll.properties2Object(properties, brokerConfig);
                    MixAll.properties2Object(properties, nettyServerConfig);
                    MixAll.properties2Object(properties, nettyClientConfig);
                    MixAll.properties2Object(properties, messageStoreConfig);

                    BrokerPathConfigHelper.setBrokerConfigPath(file);

                    System.out.println("load config properties file OK, " + file);
                    in.close();
                }
            }

            //把运行命令后面携带的入参，添加到brokerConfig配置项中
            MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), brokerConfig);

            //一般在linux环境下，会配置ROCKETMQ_HOME环境变量
            if (null == brokerConfig.getRocketmqHome()) {
                System.out.println("Please set the " + MixAll.ROCKETMQ_HOME_ENV
                        + " variable in your environment to match the location of the RocketMQ installation");
                System.exit(-2);
            }

            //可能存在多个namesrv的地址，类是ip:port;ip:port
            String namesrvAddr = brokerConfig.getNamesrvAddr();
            if (null != namesrvAddr) {
                try {
                    String[] addrArray = namesrvAddr.split(";");
                    if (addrArray != null) {
                        for (String addr : addrArray) {
                            RemotingUtil.string2SocketAddress(addr);
                        }
                    }
                } catch (Exception e) {
                    System.out
                            .printf(
                                    "The Name Server Address[%s] illegal, please set it as follows, \"127.0.0.1:9876;192.168.0.1:9876\"%n",
                                    namesrvAddr);
                    System.exit(-3);
                }
            }


            //默认是ASYNC_MASTER异步主broker
            switch (messageStoreConfig.getBrokerRole()) {
                case ASYNC_MASTER:
                case SYNC_MASTER:
                    //master broker则设置brokerId为0，非0的都是slave broker
                    brokerConfig.setBrokerId(MixAll.MASTER_ID);
                    break;
                case SLAVE:
                    if (brokerConfig.getBrokerId() <= 0) {
                        System.out.println("Slave's brokerId must be > 0");
                        System.exit(-3);
                    }

                    break;
                default:
                    break;
            }

            //set port is 10912
            messageStoreConfig.setHaListenPort(nettyServerConfig.getListenPort() + 1);

            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset();
            configurator.doConfigure(brokerConfig.getRocketmqHome() + "/conf/logback_broker.xml");
            log = LoggerFactory.getLogger(LoggerName.BrokerLoggerName);


            MixAll.printObjectProperties(log, brokerConfig);
            MixAll.printObjectProperties(log, nettyServerConfig);
            MixAll.printObjectProperties(log, nettyClientConfig);
            MixAll.printObjectProperties(log, messageStoreConfig);


            final BrokerController controller = new BrokerController(//
                    brokerConfig, //
                    nettyServerConfig, //
                    nettyClientConfig, //
                    messageStoreConfig);
            boolean initResult = controller.initialize();
            if (!initResult) {
                controller.shutdown();
                System.exit(-3);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                private volatile boolean hasShutdown = false;
                private AtomicInteger shutdownTimes = new AtomicInteger(0);


                @Override
                public void run() {
                    synchronized (this) {
                        log.info("shutdown hook was invoked, " + this.shutdownTimes.incrementAndGet());
                        if (!this.hasShutdown) {
                            this.hasShutdown = true;
                            long begineTime = System.currentTimeMillis();
                            controller.shutdown();
                            long consumingTimeTotal = System.currentTimeMillis() - begineTime;
                            log.info("shutdown hook over, consuming time total(ms): " + consumingTimeTotal);
                        }
                    }
                }
            }, "ShutdownHook"));

            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    private static void parsePropertie2SystemEnv(Properties properties) {
        if (properties == null) {
            return;
        }
        String rmqAddressServerDomain = properties.getProperty("rmqAddressServerDomain", "jmenv.tbsite.net");
        String rmqAddressServerSubGroup = properties.getProperty("rmqAddressServerSubGroup", "nsaddr");
        System.setProperty("rocketmq.namesrv.domain", rmqAddressServerDomain);
        System.setProperty("rocketmq.namesrv.domain.subgroup", rmqAddressServerSubGroup);
    }

    public static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "configFile", true, "Broker config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("m", "printImportantConfig", false, "Print important config item");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }
}
