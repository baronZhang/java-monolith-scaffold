package com.example.monolith.user.domain;

/**
 * 用户列表的查询条件。
 *
 * @param keyword 按用户名或昵称模糊搜索，空表示不限
 * @param status 按状态筛选，null 表示全部
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 */
public record UserSearchCriteria(String keyword, UserStatus status, long page, long pageSize) {}
