package com.example.monolith.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** auth_refresh_token 的 Mapper */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenPO> {

    @Select("SELECT * FROM auth_refresh_token WHERE token_hash = #{hash} FOR UPDATE")
    RefreshTokenPO lockByHash(@Param("hash") String hash);

    /** 条件里带 status = 'ACTIVE'：即使上层的锁失效了，同一个 token 也只能被标记一次 */
    @Update("UPDATE auth_refresh_token SET status = 'USED', used_at = #{usedAt}"
            + " WHERE token_hash = #{hash} AND status = 'ACTIVE'")
    int markUsed(@Param("hash") String hash, @Param("usedAt") LocalDateTime usedAt);

    @Update("UPDATE auth_refresh_token SET status = 'REVOKED' WHERE session_id = #{sessionId} AND status = 'ACTIVE'")
    int revokeActiveOfSession(@Param("sessionId") String sessionId);
}
