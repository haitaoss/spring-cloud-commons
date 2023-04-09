package cn.haitaoss.refresh;

import cn.haitaoss.refresh.entity.RefreshScopeBean1;
import cn.haitaoss.refresh.entity.RefreshScopeBean2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.cloud.bootstrap.BootstrapImportSelector;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-07 21:01
 *
 */
@SpringBootApplication
public class Main {
    /**
     * 总结用法:
     *
     * 可以通过属性
     *  spring.cloud.refresh.refreshable
     *  spring.cloud.refresh.extraRefreshable
     *  代替 @RefreshScope
     *
     * 可以设置属性 spring.cloud.refresh.enabled=false 取消 @RefreshScope 的自动注入
     * 是 spring.cloud.refresh.never-refreshable 属性记录的类就不重会新绑定属性
     * */
    public static void main(String[] args) {
        // TODOHAITAO: 2023/4/6 访问验证属性更新 GET http://127.0.0.1:8080/actuator/refresh
        // 启用 bootstrap 属性的加载
        System.setProperty("spring.cloud.bootstrap.enabled", "true");
        System.setProperty("spring.cloud.refresh.refreshable",
                Arrays.asList(RefreshScopeBean1.class.getName(), RefreshScopeBean2.class.getName())
                        .stream()
                        .collect(Collectors.joining(","))
        );
        System.setProperty("spring.cloud.refresh.extraRefreshable", Arrays.asList(Object.class.getName())
                .stream()
                .collect(Collectors.joining(",")));
        /*// 设置 bootstrap 容器的源
        System.setProperty("spring.cloud.bootstrap.sources",
                "cn.haitaoss.RefreshScope.config.MyPropertySourceLocator");*/

        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
        for (String beanDefinitionName : context.getBeanDefinitionNames()) {
            if (beanDefinitionName.contains("person")) {
                System.out.println("beanDefinitionName = " + beanDefinitionName);
            }
        }
    }
}
