package com.quick.springbootdynamictp;

import org.dromara.dynamictp.spring.annotation.EnableDynamicTp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableDynamicTp
@SpringBootApplication
public class SpringbootDynamictpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootDynamictpApplication.class, args);
    }
}
