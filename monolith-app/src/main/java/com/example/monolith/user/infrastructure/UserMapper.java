package com.example.monolith.user.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * sys_user 的 Mapper。简单的单表 CRUD 用 BaseMapper 自带的方法；有业务含义的更新写成显式 SQL，一眼能看出改了什么。
 *
 * <p>标 {@code @Mapper} 而不是在启动类上写 {@code @MapperScan}：MyBatis 的自动配置会扫描"自动配置包"下的 @Mapper，
 * Spring Modulith 做单模块测试时只把被测模块设为自动配置包，于是只加载这个模块自己的 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    @Update("UPDATE sys_user SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);

    /** 逻辑删除：deleted 写成自己的 id。条件里带 deleted = 0，重复删除时影响行数为 0。 */
    @Update("UPDATE sys_user SET deleted = id, updated_at = #{updatedAt} WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") long id, @Param("updatedAt") LocalDateTime updatedAt);
}
