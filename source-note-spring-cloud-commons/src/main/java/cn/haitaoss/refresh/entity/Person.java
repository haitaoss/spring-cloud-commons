package cn.haitaoss.refresh.entity;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-15 15:00
 *
 */
@Component
@RefreshScope
@Data
public class Person {
    public Person() {
        System.out.println("Person 构造器 ...");
    }
    @Value("${name}")
    private String name;
}
