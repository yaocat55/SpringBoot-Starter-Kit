package com.quick.springbootlombok;

import com.quick.springbootlombok.model.ImmutableConfig;
import com.quick.springbootlombok.model.OrderDTO;
import com.quick.springbootlombok.model.Product;
import com.quick.springbootlombok.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@SpringBootApplication
public class SpringbootLombokApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootLombokApplication.class, args);
    }

    @Bean
    CommandLineRunner demo() {
        return args -> {
            log.info("========== Lombok 常用注解演示开始 ==========");

            // --- @Builder + @Data ---
            User user = User.builder()
                    .id(1L)
                    .username("张三")
                    .email("zhangsan@example.com")
                    .age(25)
                    .password("secret123")
                    .build();
            log.info("[@Builder @Data] 构建用户: {}", user);

            // --- @Accessors(chain = true) 链式 setter ---
            user.setAge(26).setEmail("new-zhangsan@example.com");
            log.info("[@Accessors(chain)] 链式修改后: age={}, email={}", user.getAge(), user.getEmail());

            // --- @Value 不可变类 ---
            ImmutableConfig config = ImmutableConfig.builder()
                    .appName("LombokDemo")
                    .version("1.0.0")
                    .maxRetries(3)
                    .build();
            log.info("[@Value @Builder] 不可变配置: appName={}, version={}, maxRetries={}, timeoutMs={}",
                    config.getAppName(), config.getVersion(), config.getMaxRetries(), config.getTimeoutMs());

            // --- @With 创建副本 ---
            Product product = Product.builder()
                    .id(100L)
                    .name("机械键盘")
                    .price(299.0)
                    .stock(50)
                    .build();
            Product discounted = product.withPrice(199.0).withStock(48);
            log.info("[@With] 原产品价格: {}, 打折后: {} (新对象)", product.getPrice(), discounted.getPrice());

            // --- @Getter @Setter @ToString @EqualsAndHashCode ---
            OrderDTO order = new OrderDTO();
            order.setOrderId("ORD-20240001");
            order.setCustomerName("李四");
            order.setInternalNote("内部备注不会出现在 toString 中");
            order.setAmount(new BigDecimal("99.90"));
            order.setItems(List.of("商品A", "商品B"));
            log.info("[@Getter @Setter @ToString] 订单: {}", order);

            log.info("========== Lombok 常用注解演示结束 ==========");
        };
    }
}
