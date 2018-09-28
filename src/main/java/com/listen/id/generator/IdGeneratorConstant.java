package com.listen.id.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IdGeneratorConstant {

    private static final Logger logger = LoggerFactory.getLogger(IdGeneratorConstant.class);

    public static final Map<String, IdGenerator> ID_GENERATORS = new ConcurrentHashMap<>();

    public WorkIdGenerator workIdGenerator;

    // 初始化的时候注入workId生成器
    public IdGeneratorConstant(WorkIdGenerator workIdGenerator) {
        this.workIdGenerator = workIdGenerator;
    }

    public Long getId(Class<?> className) {
        return getId(className.getName());
    }

    /**
     * 根据className生成id
     *
     * @param className
     * @return
     */
    public Long getId(String className) {

        // 如果没有实例在这个Map里面
        if (!ID_GENERATORS.containsKey(className)) {

            synchronized (IdGeneratorConstant.class) {

                if (!ID_GENERATORS.containsKey(className)) {

                    logger.info("生成新的IdGenerator: {}-{}", className, workIdGenerator.getWorkId());

                    // generate a new instance
                    IdGenerator newId = new IdGenerator(workIdGenerator.getWorkId());

                    ID_GENERATORS.put(className, newId);
                }
            }
        }

        // 无论如何都需要从**这个Map**里面去取得实例再产生ID
        // 需要保证并发情况下是一个实例产生的ID
        return ID_GENERATORS.get(className).generate();
    }
}
