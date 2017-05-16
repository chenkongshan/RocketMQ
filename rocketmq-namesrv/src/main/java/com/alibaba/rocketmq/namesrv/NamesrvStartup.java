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
package com.alibaba.rocketmq.namesrv;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.alibaba.rocketmq.common.MQVersion;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.constant.LoggerName;
import com.alibaba.rocketmq.common.namesrv.NamesrvConfig;
import com.alibaba.rocketmq.remoting.netty.NettyServerConfig;
import com.alibaba.rocketmq.remoting.netty.NettySystemConfig;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;
import com.alibaba.rocketmq.srvutil.ServerUtil;
import com.alibaba.rocketmq.tools.path.ProjectPath;
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
public class NamesrvStartup {
    public static Properties properties = null;
    public static CommandLine commandLine = null;

    public static void main(String[] args) {
        List<String> list = new ArrayList<String>();
        //list.add("-p");
        //args = list.toArray(new String[0]);

        //set rocketmq home
        System.setProperty(MixAll.ROCKETMQ_HOME_PROPERTY, ProjectPath.getRoot());
        main0(args);
    }

    public static NamesrvController main0(String[] args) {
        //set "rocketmq.remoting.version"
        System.setProperty(RemotingCommand.RemotingVersionKey,
                //137 version V3_5_8
                Integer.toString(MQVersion.CurrentVersion));


        if (null == System.getProperty(NettySystemConfig.SystemPropertySocketSndbufSize)) {
            //sender buffer size
            NettySystemConfig.socketSndbufSize = 4096;
        }


        if (null == System.getProperty(NettySystemConfig.SystemPropertySocketRcvbufSize)) {
            //receiver buffer size
            NettySystemConfig.socketRcvbufSize = 4096;
        }

        try {
            //PackageConflictDetect.detectFastjson();

            //build help namesrvAddr option
            Options options = ServerUtil.buildCommandlineOptions(new Options());
            //主要是用来把入参，根据options转换为CommonLine
            commandLine =
                    ServerUtil.parseCmdLine("mqnamesrv", args,
                            //添加-c configFile option
                            //添加 -p printConfigItem option
                            buildCommandlineOptions(options),
                            //新版本的commons.cli使用DefaultParser
                            new PosixParser());
            if (null == commandLine) {
                System.exit(-1);
                return null;
            }


            final NamesrvConfig namesrvConfig = new NamesrvConfig();
            final NettyServerConfig nettyServerConfig = new NettyServerConfig();
            //设置ServerBootStrap的监听端口为9876
            nettyServerConfig.setListenPort(9876);
            //如果入参存在namesrv的config配置
            if (commandLine.hasOption('c')) {
                String file = commandLine.getOptionValue('c');
                if (file != null) {
                    InputStream in = new BufferedInputStream(new FileInputStream(file));
                    properties = new Properties();
                    properties.load(in);
                    MixAll.properties2Object(properties, namesrvConfig);
                    MixAll.properties2Object(properties, nettyServerConfig);
                    System.out.println("load config properties file OK, " + file);
                    in.close();
                }
            }


            if (commandLine.hasOption('p')) {
                MixAll.printObjectProperties(null, namesrvConfig);
                MixAll.printObjectProperties(null, nettyServerConfig);
                System.exit(0);
            }

            //把入参从CommonLine中拷贝到namesrvConfig中
            MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), namesrvConfig);

            if (null == namesrvConfig.getRocketmqHome()) {
                System.out.println("Please set the " + MixAll.ROCKETMQ_HOME_ENV
                        + " variable in your environment to match the location of the RocketMQ installation");
                System.exit(-2);
            }

            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset();
            configurator.doConfigure(namesrvConfig.getRocketmqHome() + "/conf/logback_namesrv.xml");
            final Logger log = LoggerFactory.getLogger(LoggerName.NamesrvLoggerName);


            MixAll.printObjectProperties(log, namesrvConfig);
            MixAll.printObjectProperties(log, nettyServerConfig);


            final NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig);
            //Controller初始化
            boolean initResult = controller.initialize();
            if (!initResult) {
                controller.shutdown();
                System.exit(-3);
            }

            //关闭资源的shutdownHook
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

            //启动资源，主要就是启动NettyRemotingServer
            controller.start();

            String tip = "The Name Server boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();
            log.info(tip);
            System.out.println(tip);

            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    public static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "configFile", true, "Name server config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }
}
