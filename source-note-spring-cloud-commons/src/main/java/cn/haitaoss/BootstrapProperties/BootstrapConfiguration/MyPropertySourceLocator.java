package cn.haitaoss.BootstrapProperties.BootstrapConfiguration;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-15 15:04
 *
 */
public class MyPropertySourceLocator implements PropertySourceLocator {

    public MyPropertySourceLocator() {
        System.out.println("MyPropertySourceLocator...构造器");
    }

    @Resource
    private ApplicationContext applicationContext;

    @Value("${dynamicConfigFile}")
    private String filePath;

    @Override
    public PropertySource<?> locate(Environment environment) {
        PropertySource<?> propertySource;
        try {
            // 也可以是网络资源
            propertySource = new YamlPropertySourceLoader()
                    .load("haitao-propertySource", applicationContext.getResource(filePath)).get(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return propertySource;
    }

}
