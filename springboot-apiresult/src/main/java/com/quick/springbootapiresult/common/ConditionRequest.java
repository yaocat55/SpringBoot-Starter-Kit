package com.quick.springbootapiresult.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 通用条件查询请求 —— 继承 PageRequest，分页 + 条件查询一个对象搞定。
 *
 * <h3>前端传参</h3>
 * <pre>{@code
 * GET /api/users/condition?blurry=张&createBeginTime=2025-01-01&createEndTime=2025-06-30&betweenTime=2025-01-01,2025-06-30&pageNo=1&pageSize=10&sortField=age,desc
 * }</pre>
 *
 * <h3>Controller 用法</h3>
 * <pre>{@code
 * @GetMapping("/condition")
 * public ApiResult<PageResult<User>> search(ConditionRequest req) {
 *     return ApiResult.success(userService.searchByCondition(req));
 * }
 * }</pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConditionRequest extends PageRequest {

    /** 模糊搜索关键词（匹配 name / email） */
    private String blurry;

    /** 创建日期范围（逗号分隔："2025-01-01,2025-06-30"） */
    private List<String> betweenTime;

    /** 创建开始时间 */
    private String createBeginTime;

    /** 创建结束时间 */
    private String createEndTime;

    // ==================== 便捷方法 ====================

    /** 是否有模糊搜索关键词 */
    public boolean hasBlurry() {
        return blurry != null && !blurry.isBlank();
    }

    /** 获取日期范围的起始值（betweenTime[0]） */
    public String getRangeBegin() {
        if (betweenTime != null && betweenTime.size() >= 2) {
            return betweenTime.get(0);
        }
        return createBeginTime;
    }

    /** 获取日期范围的结束值（betweenTime[1]） */
    public String getRangeEnd() {
        if (betweenTime != null && betweenTime.size() >= 2) {
            return betweenTime.get(1);
        }
        return createEndTime;
    }

    /** 是否有任何查询条件 */
    public boolean hasAnyCondition() {
        return hasBlurry()
                || (betweenTime != null && !betweenTime.isEmpty())
                || createBeginTime != null
                || createEndTime != null;
    }
}
