package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;

/**
 * 验证码输错。抛出前已经把错误次数加 1，所使用的事务对这个异常不回滚，
 * 这样错误次数能真正保存下来，达到上限后验证码作废。
 */
public class EmailCodeRejectedException extends BusinessException {

    public EmailCodeRejectedException(String message) {
        super(ErrorCode.EMAIL_CODE_INVALID, message);
    }
}
