package com.quick.springbootrocketmq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
class SpringbootRocketmqApplicationTests {

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @Test
    void contextLoads() {
    }

}
