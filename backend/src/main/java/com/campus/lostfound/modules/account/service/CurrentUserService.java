package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.mapper.MeRow;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserMapper userMapper;

    public CurrentUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Me getMe(long userId) {
        MeRow row = userMapper.findMe(userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return Me.from(row);
    }
}
