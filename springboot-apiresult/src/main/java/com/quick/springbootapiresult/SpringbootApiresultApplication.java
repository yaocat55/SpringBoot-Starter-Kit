package com.quick.springbootapiresult;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.quick.springbootapiresult.mapper")
public class SpringbootApiresultApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootApiresultApplication.class, args);
    }

}
