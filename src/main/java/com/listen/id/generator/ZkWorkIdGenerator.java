package com.listen.id.generator;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class ZkWorkIdGenerator extends WorkIdGenerator{

    private ZooKeeper zookeeper;

    private static final int ZOOKEEPER_SESSION_TIMEOUT = 120000;

    private final static String zkRootPath = "/id_machines";

    private final static String zkGeneratorPath = zkRootPath + "/workIdGeneratorNode/";

    private final static String zkSavePath = zkRootPath + "/workIdSaveNode/";

    private CountDownLatch connectedSemaphore = new CountDownLatch(1);

    public ZkWorkIdGenerator(String zkAddress) throws IOException, InterruptedException {
        super();
        this.zookeeper = new ZooKeeper(zkAddress, ZOOKEEPER_SESSION_TIMEOUT, event -> {

            if (Watcher.Event.KeeperState.SyncConnected == event.getState()) {
                connectedSemaphore.countDown();
            } else {
                System.out.println(event.getState());
            }

        });
        connectedSemaphore.await();
    }

    @Override
    public void createRootNodeIfNotExist() {
        try {

            final Stat exists = zookeeper.exists(zkRootPath, true);

            if (Objects.isNull(exists)) {

                //
                zookeeper.create(
                        zkRootPath,
                        "".getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);

                // 消除最后一位的影响
                zookeeper.create(
                        zkGeneratorPath.substring(0, zkGeneratorPath.length() - 1),
                        "".getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);

                //
                zookeeper.create(
                        zkSavePath.substring(0, zkSavePath.length() - 1),
                        "".getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }

        } catch (KeeperException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int queryExistWorkId() {

        String path = zkSavePath + ipAddress;

        try {

            final Stat exists = zookeeper.exists(path, true);

            if (Objects.nonNull(exists)) {

                final byte[] data = zookeeper.getData(path, false, null);

                return Integer.parseInt(new String(data));
            } else {
                // 没有找到节点数据就返回 -1
                return -1;
            }

        } catch (KeeperException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public int generateWorkId() {
        try {

            // Zookeeper节点路径example. /machines/workIdGeneratorNode/192.168.3.2000000000001
            String path = zkGeneratorPath + ipAddress;

            final String createPath = zookeeper
                    .create(
                            path,
                            "".getBytes(),
                            ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT_SEQUENTIAL);

            final String workId = gainWorkIdFromNodePath(createPath);

            // zk需要在另外一个节点存储一下这个
            saveWorkId(workId);

            return Integer.parseInt(workId);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 获取workId
     *
     * @param createPath
     * @return
     */
    private String gainWorkIdFromNodePath(String createPath) {

        final String substring = createPath.substring(createPath.length() - 10);

        // 第一个数字 0000000000
        if (Objects.equals("0000000000", substring)) {
            return "0";
        }

        final char[] chars = substring.toCharArray();
        int mark = 0;

        for (int i = 0; i < chars.length; i++) {
            if (chars[i] != "0".charAt(0)) {
                mark = i;
                break;
            }
        }

        return substring.substring(mark);

    }

    /**
     * 保存workId
     *
     * @param workId
     * @throws KeeperException
     * @throws InterruptedException
     */
    private void saveWorkId(String workId) throws KeeperException, InterruptedException {

        // example. /machines/workIdSaveNode/192.168.3.200
        String path = zkSavePath + ipAddress;

        zookeeper.create(
                path,
                workId.getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT);

    }
}
