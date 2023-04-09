package cn.haitaoss.refresh.controller;

import cn.haitaoss.refresh.config.InfoProperties;
import cn.haitaoss.refresh.entity.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-03-08 09:57
 *
 */
@Controller
public class HelloController {
    @Autowired
    private InfoProperties infoProperties;

    @Autowired
    private Person person;

    @Value("${management.endpoints.web.basePath:/actuator}")
    private String basePath;

    @RequestMapping({"", "/"})
    public String index() {
        return "redirect:" + basePath;
    }

    @RequestMapping("/show")
    @ResponseBody
    public Object show() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("infoProperties", infoProperties.toString());
        map.put("person", person.toString());
        return map;
    }
}
