package com.example.monolith.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** auth_session 的 Mapper */
@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionPO> {

    /** 行锁：按主键加锁只锁这一行（InnoDB 的记录锁），不影响其他会话 */
    @Select("SELECT * FROM auth_session WHERE id = #{id} FOR UPDATE")
    AuthSessionPO lockById(@Param("id") String id);

    @Update("UPDATE auth_session SET status = #{status}, revoke_reason = #{reason}, updated_at = #{updatedAt}"
            + " WHERE id = #{id}")
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Select("SELECT id FROM auth_session WHERE user_id = #{userId} AND status = 'ACTIVE'")
    List<String> findActiveIds(@Param("userId") long userId);
}
