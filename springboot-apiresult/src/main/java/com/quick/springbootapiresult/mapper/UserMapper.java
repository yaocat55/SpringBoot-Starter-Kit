package com.quick.springbootapiresult.mapper;

import com.quick.springbootapiresult.common.BaseMapper;
import com.quick.springbootapiresult.common.PageRequest;
import com.quick.springbootapiresult.model.User;
import com.quick.springbootapiresult.model.UserQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper —— 继承 BaseMapper，自动获得 searchCount / searchByCondition。
 *
 * <h3>两种查询方式</h3>
 * <ol>
 *   <li><b>简单分页+模糊</b>：selectPage / countByBlurry —— 只传关键词 + PageRequest</li>
 *   <li><b>生产级条件查询</b>：searchByCondition / searchCount —— 传 UserQuery（分页+模糊+日期+精确条件合一），XML 里 include BaseMapper.paginationSql</li>
 * </ol>
 */
@Mapper
public interface UserMapper extends BaseMapper<User, UserQuery> {

    // ==================== 简单分页 + 模糊（快速上手用） ====================

    List<User> selectPage(@Param("blurry") String blurry, @Param("page") PageRequest page);

    long countByBlurry(@Param("blurry") String blurry);

    // ==================== 基础 CRUD ====================

    User findById(Long id);

    User findByEmail(@Param("email") String email);

    int insert(User user);

    int update(User user);

    int deleteById(@Param("id") Long id);
}
