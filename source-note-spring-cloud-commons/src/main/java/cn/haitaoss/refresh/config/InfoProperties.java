package cn.haitaoss.refresh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-15 15:02
 *
 */
@Component
@ConfigurationProperties(prefix = "info")
@Data
public class InfoProperties {
    public InfoProperties() {
        System.out.println("InfoProperties 构造器 ...");
    }

    private String name;
    private String address;
}
