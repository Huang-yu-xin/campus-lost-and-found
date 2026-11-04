package com.campus.lostfound.modules.claim.service;

import com.campus.lostfound.modules.claim.mapper.ContactPermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 联系方式授权（FR-06）。归丙（认领模块）维护：授权设置、撤回、专用查询由丙补充。
 * 目前只有甲为 A05 先行实现的“资料变更时撤回”，需经丙复核。
 */
@Service
public class ContactPermissionService {

    private final ContactPermissionMapper contactPermissionMapper;

    public ContactPermissionService(ContactPermissionMapper contactPermissionMapper) {
        this.contactPermissionMapper = contactPermissionMapper;
    }

    /**
     * 用户改了手机号或邮箱后，撤回他在所有认领中对该字段的授权，防止对方静默看到新值。
     * 只撤回改动的那个字段，另一字段的授权不受影响。须在资料修改的同一事务中调用。
     * @return 撤回的授权条数
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllForGrantor(long grantorId, ContactField field) {
        return contactPermissionMapper.revokeAllForGrantor(grantorId, field.name());
    }
}
