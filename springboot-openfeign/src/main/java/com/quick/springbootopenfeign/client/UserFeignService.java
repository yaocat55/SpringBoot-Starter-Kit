package com.quick.springbootopenfeign.client;

import com.quick.springbootopenfeign.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 封装 Feign 调用的 Service 层。
 * <p>
 * 在实际项目中，业务逻辑放在 Service 里，Feign Client 只是"如何调 HTTP"的声明。
 * Service 封装了：多步调用编排、异常处理、结果转换、缓存等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFeignService {

    private final UserFeignClient userFeignClient;

    public User getUser(Long id) {
        log.info("[Service] 调用 Feign 查询用户 {}", id);
        return userFeignClient.getById(id);
    }

    public List<User> getAllUsers() {
        log.info("[Service] 调用 Feign 查询全部用户");
        return userFeignClient.listAll();
    }

    public User addUser(String name, String email) {
        User user = new User(null, name, email);
        log.info("[Service] 调用 Feign 创建用户: name={}, email={}", name, email);
        return userFeignClient.create(user);
    }

    public User modifyUser(Long id, String name, String email) {
        User user = new User(id, name, email);
        log.info("[Service] 调用 Feign 更新用户 {}", id);
        return userFeignClient.update(id, user);
    }

    public Map<String, Object> removeUser(Long id) {
        log.info("[Service] 调用 Feign 删除用户 {}", id);
        return userFeignClient.delete(id);
    }
}
