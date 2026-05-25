package com.quick.springbootapiresult.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 分页响应实体 —— Service 层返回这个，Controller 包进 ApiResult。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * PageResult<User> result = PageResult.build(request, totalCount, dataList);
 * return ApiResult.success(result);
 * }</pre>
 *
 * <h3>返回 JSON</h3>
 * <pre>{@code
 * {
 *   "pageNo": 1,
 *   "pageSize": 10,
 *   "totalPage": 2,
 *   "totalCount": 20,
 *   "data": [ ... ]
 * }
 * }</pre>
 */
@Data
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    /** 当前页码 */
    private Integer pageNo;

    /** 每页大小 */
    private Integer pageSize;

    /** 总页数（自动计算） */
    private Integer totalPage;

    /** 总记录数 */
    private Integer totalCount;

    /** 当前页数据 */
    private List<T> data;

    private PageResult() {}

    /**
     * 构建空页。
     */
    public static <T> PageResult<T> buildEmpty(PageRequest request) {
        return build(request, 0, new ArrayList<>(0));
    }

    /**
     * 构建分页结果。
     *
     * @param request    分页请求（取 pageNo / pageSize）
     * @param totalCount 总记录数
     * @param data       当前页数据
     */
    public static <T> PageResult<T> build(PageRequest request, Integer totalCount, List<T> data) {
        Integer totalPage = calcTotalPage(request.getPageSize(), totalCount);
        return new PageResult<>(
                request.getPageNo(),
                request.getPageSize(),
                totalPage,
                totalCount,
                data != null ? data : new ArrayList<>(0)
        );
    }

    /** 计算总页数 */
    private static Integer calcTotalPage(Integer pageSize, Integer totalCount) {
        if (pageSize == null || totalCount == null || pageSize <= 0 || totalCount <= 0) {
            return 0;
        }
        return totalCount % pageSize == 0 ? totalCount / pageSize : totalCount / pageSize + 1;
    }
}
