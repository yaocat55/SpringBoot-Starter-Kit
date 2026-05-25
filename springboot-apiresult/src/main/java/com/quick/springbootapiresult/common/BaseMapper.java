package com.quick.springbootapiresult.common;

import java.util.List;

/**
 * 公共 Mapper 基类 —— 所有 Mapper 继承它即可获得分页条件查询能力。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User, UserQuery> {
 *     // 自动继承 searchCount / searchByCondition
 *     // 这里只写 User 独有的方法（findById、insert、update 等）
 * }
 * }</pre>
 *
 * @param <K> 实体类型
 * @param <V> 查询条件类型（须继承 PageRequest）
 */
public interface BaseMapper<K, V> {

    /**
     * 根据条件统计数量。
     *
     * @param v 查询条件实体
     * @return 符合条件的总记录数
     */
    int searchCount(V v);

    /**
     * 根据条件查询数据（自动分页 + 排序）。
     *
     * @param v 查询条件实体
     * @return 当前页数据
     */
    List<K> searchByCondition(V v);
}
