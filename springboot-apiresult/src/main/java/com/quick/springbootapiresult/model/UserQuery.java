package com.quick.springbootapiresult.model;

import com.quick.springbootapiresult.common.ConditionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户查询条件实体 —— 继承 ConditionRequest，分页 + 模糊 + 日期范围 + User 专用筛选一个对象搞定。
 *
 * <h3>前端传参示例</h3>
 * <pre>{@code
 * GET /api/users/condition?blurry=张&status=active&role=admin&betweenTime=2025-01-01,2025-12-31&pageNo=1&pageSize=10&sortField=age,desc
 * }</pre>
 *
 * <h3>Mapper XML 用法</h3>
 * <p>queryWhere 片段里直接取这些字段：{@code #{status}}、{@code #{role}}、{@code #{idList}} 等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends ConditionRequest {

    /** 用户 ID */
    private Long id;

    /** 用户 ID 列表（IN 查询） */
    private List<Long> idList;

    /** 邮箱精确匹配 */
    private String email;

    /** 状态精确匹配：active / inactive */
    private String status;

    /** 角色精确匹配：admin / editor / viewer */
    private String role;
}
