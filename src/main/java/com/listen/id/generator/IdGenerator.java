package com.listen.id.generator;


import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义 ID 生成器
 * ID 生成规则: ID长达 64 bits
 * | 最大值 2199023255552 | 最大值 256 | 最大值 1024 | 最大值 32 |
 * | 41 bits: Timestamp (毫秒) | 3 bits: 区域（机房） | 10 bits: 机器编号 | 10 bits: 序列号 |
 */
public final class IdGenerator {

    // Epoch millis: Thu, 04 Nov 2010 01:42:54 GMT
    public static final long twepoch = 1288834974657L;

    // 区域标志位数
    private final static long regionIdBits = 3L;

    // 机器标识位数
    private final static long workerIdBits = 10L;

    // 序列号识位数
    private final static long sequenceBits = 10L;

    // 区域标志ID最大值
    private final static long MAX_REGIONID = ~(-1L << regionIdBits);

    // 机器ID最大值
    private final static long MAX_WORKERID = ~(-1L << workerIdBits);

    // 序列号ID最大值
    private final static long SEQUENCE_MASK = ~(-1L << sequenceBits);

    // 机器ID偏左移10位
    private final static long WORKERID_SHIFT = sequenceBits;

    // 业务ID偏左移20位
    private final static long REGIONID_SHIFT = sequenceBits + workerIdBits;

    // 时间毫秒左移23位
    private final static long TIMESTAMP_LEFTSHIFT = sequenceBits + workerIdBits + regionIdBits;

    private long lastTimestamp = -1L;

    private AtomicLong sequence = new AtomicLong(0L);

    private final long workerId;

    private final long regionId;

    // public static IdGenerator idGenerator = new IdGenerator(RandomUtils.nextLong(0, 100));

    public IdGenerator() {

        this.regionId = getRegionId(MAX_REGIONID);
        this.workerId = getMaxWorkerId(regionId, MAX_WORKERID);

    }

    private IdGenerator(long workerId, long regionId) {

        // 如果超出范围就抛出异常
        if (workerId > MAX_WORKERID || workerId < 0) {
            throw new IllegalArgumentException("worker Id can't be greater than %d or less than 0");
        }
        if (regionId > MAX_REGIONID || regionId < 0) {
            throw new IllegalArgumentException("regionId Id can't be greater than %d or less than 0");
        }

        this.workerId = workerId;
        this.regionId = regionId;
    }

    public IdGenerator(long workerId) {
        // 如果超出范围就抛出异常
        if (workerId > MAX_WORKERID || workerId < 0) {
            throw new IllegalArgumentException("worker Id can't be greater than %d or less than 0");
        }
        this.workerId = workerId;
        this.regionId = 0;
    }

    public long generate() {
        return this.nextId(false, 0);
    }

    public long generate(long busid) {
        return this.nextId(true, busid);
    }

    public static long generateBaseOnTime(long timestamp) {
        return (timestamp - twepoch) << TIMESTAMP_LEFTSHIFT;
    }


    /**
     * 实际产生代码的
     *
     * @param isPadding
     * @param busId
     * @return
     */
    private synchronized long nextId(boolean isPadding, long busId) {

        long timestamp = timeGen();
        long paddingnum = regionId;

        if (isPadding) {
            paddingnum = busId;
        }

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards.  Refusing to generate id for "
                    + (lastTimestamp - timestamp)
                    + " milliseconds");
        }

        // 如果上次生成时间和当前时间相同,在同一毫秒内
        if (lastTimestamp == timestamp) {

            // sequence自增，因为sequence只有10bit，所以和sequenceMask相与一下，去掉高位
            // sequence = (sequence + 1) & SEQUENCE_MASK;

            sequence.set(sequence.incrementAndGet() & SEQUENCE_MASK);

            // 判断是否溢出,也就是每毫秒内超过1024，当为1024时，与sequenceMask相与，sequence就等于0
            // if (sequence == 0L) {
            if (sequence.get() == 0L) {
                // 自旋等待到下一毫秒
                timestamp = tailNextMillis(lastTimestamp);
            }

        } else {
            // 如果和上次生成时间不同,重置sequence，就是下一毫秒开始，sequence计数重新从0开始累加,
            // 为了保证尾数随机性更大一些,最后一位设置一个随机数
            // sequence = new SecureRandom().nextInt(10);
            sequence.set(new SecureRandom().nextInt(10));
        }

        lastTimestamp = timestamp;

        return ((timestamp - twepoch) << TIMESTAMP_LEFTSHIFT) | (paddingnum << REGIONID_SHIFT)
                | (workerId << WORKERID_SHIFT) | sequence.get();
    }

    // 防止产生的时间比之前的时间还要小（由于NTP回拨等问题）,保持增量的趋势.
    private long tailNextMillis(final long lastTimestamp) {
        long timestamp = this.timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = this.timeGen();
        }
        return timestamp;
    }

    // 获取当前的时间戳
    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * <p>
     * 获取 maxWorkerId
     * </p>
     */
    private static long getMaxWorkerId(long regionId, long maxWorkerId) {
        StringBuffer mpid = new StringBuffer();
        mpid.append(regionId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (name != null && !Objects.equals(name, "")) {
            /*
             * GET jvmPid
             */
            mpid.append(name.split("@")[0]);
        }
        /*
         * MAC + PID 的 hashcode 获取16个低位
         */
        return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
    }

    /**
     * <p>
     * 数据标识id部分
     * </p>
     */
    private static long getRegionId(long maxRegionId) {
        long id = 0L;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            if (network == null) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                id = ((0x000000FF & (long) mac[mac.length - 1])
                        | (0x0000FF00 & (((long) mac[mac.length - 2]) << 8))) >> 6;
                id = id % (maxRegionId + 1);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("regionId Id can't be greater than %d or less than 0");
        }
        return id;
    }

    /**
     * 从ID中获取时间
     *
     * @param id
     * @return
     */
    public static Date getDateFromId(Long id) {

        return new Date(id >> 23 + twepoch);

    }

    public static void main(String args[]) {
        /*
        11011000001111010100100111110000100011
        1101100000111101010010011111000010001100000000000000000000000
        1101100000111101010010011111000010001100000000000000000100110
        */
        Long timeStamp = System.currentTimeMillis();
        System.out.println("timestamp: " + timeStamp);
        System.out.println(Long.toBinaryString(2199023255552L));
        System.out.println(Long.toBinaryString(timeStamp - twepoch));
        Long id = IdGenerator.generateBaseOnTime(timeStamp);
        System.out.println(Long.toBinaryString(id));

        timeStamp = System.currentTimeMillis();
        System.out.println(Long.toBinaryString(timeStamp - twepoch));
        id = IdGenerator.generateBaseOnTime(timeStamp);
        System.out.println(Long.toBinaryString(id));

        IdGenerator idGenerator = new IdGenerator(1);
        System.out.println(Long.toBinaryString(idGenerator.generate()));
    }


}
