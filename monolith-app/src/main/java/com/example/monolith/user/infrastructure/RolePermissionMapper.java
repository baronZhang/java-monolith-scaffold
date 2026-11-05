package com.example.monolith.user.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 角色 → 权限码。数据量很小、变化很少，真实项目里会加缓存。 */
@Mapper
public interface RolePermissionMapper {

    @Select("SELECT permission FROM sys_role_permission WHERE role = #{role} ORDER BY permission")
    List<String> findPermissions(@Param("role") String role);
}
