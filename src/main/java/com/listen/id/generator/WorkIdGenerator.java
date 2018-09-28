package com.listen.id.generator;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 */
public abstract class WorkIdGenerator {

    protected String ipAddress;

    /**
     * 检查Ip是否已经被注册过
     */
    public WorkIdGenerator() {

        try {
            InetAddress ip = InetAddress.getLocalHost();
            ipAddress = ip.getHostAddress();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * 获取workId
     *
     * @return
     */
    public int getWorkId() {

        createRootNodeIfNotExist();

        final int existWorkId = queryExistWorkId();

        return existWorkId < 0 ? generateWorkId() : existWorkId;

    }

    /**
     * 判断Root节点是否存在，如果不存在就创建节点
     */
    public abstract void createRootNodeIfNotExist();

    /**
     * 查询存在的workId,如果不存在就返回 -1
     *
     * @return
     */
    public abstract int queryExistWorkId();

    /**
     * 根据 IP Address 产生一个新的workId
     *
     * @return
     */
    public abstract int generateWorkId();


}
