package com.alibaba.rocketmq.tools.path;

/**
 * Author: Administrator
 * Date: 2017/5/15
 */
public class ProjectPath {

    public static String getRoot() {
        String path = System.getProperty("user.dir");
        return path;
    }
}
