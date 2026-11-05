package com.example.monolith.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void 不能修改自己的状态() {
        User admin = user(1, UserStatus.ACTIVE);

        assertThatThrownBy(() -> admin.changeStatus(UserStatus.DISABLED, 1)).hasMessage("不能禁用当前登录的账号");
        assertThat(admin.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 状态没有变化时返回false_调用方据此决定要不要写库和发事件() {
        User editor = user(2, UserStatus.ACTIVE);

        assertThat(editor.changeStatus(UserStatus.ACTIVE, 1)).isFalse();
        assertThat(editor.changeStatus(UserStatus.DISABLED, 1)).isTrue();
        assertThat(editor.isEnabled()).isFalse();
    }

    @Test
    void 不能删除自己() {
        assertThatThrownBy(() -> user(1, UserStatus.ACTIVE).assertDeletableBy(1))
                .hasMessage("不能删除当前登录的账号");
    }

    private static User user(long id, UserStatus status) {
        return new User(id, "u" + id, "昵称", "u@example.com", "editor", status, null, Instant.EPOCH);
    }
}
