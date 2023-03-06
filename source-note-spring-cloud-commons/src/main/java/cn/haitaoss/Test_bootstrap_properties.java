package cn.haitaoss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.stream.Stream;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 */
@EnableAutoConfiguration
public class Test_bootstrap_properties {
    public static void main(String[] args) {
        // 是否启动 bootstrap 属性文件的建议
        System.setProperty("spring.cloud.bootstrap.enabled", "true");
        // 设置属性文件的搜索目录 或者是 属性文件
        System.setProperty("spring.cloud.bootstrap.location", "");
        System.setProperty(
                "spring.cloud.bootstrap.additional-location",
                "optional:classpath:/config/haitao/,classpath:/haitao.properties"
        );

        //        System.setProperty("spring.profiles.active", "haitao"); // 设置 profile
        //        System.setProperty("spring.cloud.bootstrap.name", "bootstrap-haitao"); // 修改默认属性文件的名字
        // 测试读取属性
        ConfigurableApplicationContext context = SpringApplication.run(Test_bootstrap_properties.class, args);
        ConfigurableEnvironment environment = context.getEnvironment();
        Stream.iterate(1, i -> i + 1)
                .limit(4)
                .map(i -> "p" + i)
                .forEach(name -> System.out.println(
                        String.format("key:%s \t valus: %s", name, environment.getProperty(name))));
    }
}