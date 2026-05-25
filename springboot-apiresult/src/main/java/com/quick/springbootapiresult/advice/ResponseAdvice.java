package com.quick.springbootapiresult.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quick.springbootapiresult.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 返回体自动包装 —— Controller 返回非 ApiResult 类型时，自动包装成 ApiResult.success(data)。
 *
 * <h3>效果</h3>
 * <pre>{@code
 * // Controller 里写的
 * @GetMapping("/hello")
 * public String hello() { return "world"; }
 *
 * // 前端实际收到
 * {"code":200, "msg":"success", "data":"world", "timestamp":...}
 * }</pre>
 *
 * <h3>排除路径</h3>
 * Swagger、actuator 等路径不包装，走原始返回。
 */
@RestControllerAdvice(basePackages = "com.quick.springbootapiresult")
@RequiredArgsConstructor
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    /** 这些路径前缀不自动包装 */
    private static final String[] EXCLUDE_PREFIXES = {
            "/swagger", "/v3/api-docs", "/actuator", "/error"
    };

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果 Controller 已经返回 ApiResult，不再包装
        return !ApiResult.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 排除 swagger / actuator / error
        String path = request.getURI().getPath();
        for (String prefix : EXCLUDE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return body;
            }
        }

        // String 类型特殊处理：序列化为 JSON 字符串
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(ApiResult.success(body));
            } catch (Exception e) {
                return body;  // 序列化失败兜底
            }
        }

        // 已经是 ApiResult 则不包装（supports 已经过滤，此处兜底）
        if (body instanceof ApiResult) {
            return body;
        }

        // null 也包装
        return ApiResult.success(body);
    }

}
