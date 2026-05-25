package com.quick.springbootapiresult.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 分页请求实体 —— 所有分页接口统一用这个接收参数。
 *
 * <h3>前端传参</h3>
 * <pre>{@code
 * GET /api/users?pageNo=1&pageSize=20&sortField=age,desc&sortField=name,asc
 * }</pre>
 *
 * <h3>Controller 用法</h3>
 * <pre>{@code
 * @GetMapping
 * public ApiResult<PageResult<User>> list(PageRequest page) {
 *     page.validate();
 *     return ApiResult.success(userService.search(keyword, page));
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest implements Serializable {

    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 页码（1-based），默认第 1 页 */
    private Integer pageNo = 1;

    /** 每页大小，默认 10 条 */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /** 排序字段列表，格式: "字段名,asc" 或 "字段名,desc"，支持多列排序 */
    private List<String> sortField;

    /** 校验参数合法性 */
    public void validate() {
        if (pageNo == null || pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > 100) {
            throw new IllegalArgumentException("pageSize 不能超过 100，当前值: " + pageSize);
        }
    }

    /**
     * MyBatis LIMIT 的起始位置。
     * 方法名必须是 getXxx，MyBatis XML 里 #{page.pageBegin} 才能取到值。
     */
    public Integer getPageBegin() {
        validate();
        return (pageNo - 1) * pageSize;
    }

    /**
     * 把 sortField 列表拼成 SQL 的 ORDER BY 字符串。
     * 例如 sortField=["age,desc", "name,asc"] → "age desc, name asc"
     */
    public String getSortString() {
        if (sortField == null || sortField.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String field : sortField) {
            String[] parts = field.split(",");
            if (parts.length == 2) {
                sb.append(parts[0].trim()).append(" ").append(parts[1].trim().toUpperCase()).append(", ");
            }
        }
        if (sb.length() > 0) {
            sb.delete(sb.length() - 2, sb.length());  // 去掉末尾的 ", "
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
