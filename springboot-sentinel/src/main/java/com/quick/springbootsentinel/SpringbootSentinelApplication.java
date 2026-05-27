package com.quick.springbootsentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class SpringbootSentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootSentinelApplication.class, args);
    }

}
