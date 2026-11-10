package com.campus.lostfound.modules.claim.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ContactPermissionRequest;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ContactPermissionView;
import com.campus.lostfound.modules.claim.mapper.ClaimMapper;
import com.campus.lostfound.modules.claim.mapper.ContactPermissionMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 联系方式授权（FR-06）。授权对象只由认领关系推导，不接受客户端传用户编号；
 * 授权在认领 CONFIRMED 且物品 PUBLISHED 时生效，交接完成或违规下架后失效。
 */
@Service
public class ContactPermissionService {

    private final ContactPermissionMapper mapper;
    private final ClaimMapper claimMapper;

    public ContactPermissionService(ContactPermissionMapper mapper, ClaimMapper claimMapper) {
        this.mapper = mapper;
        this.claimMapper = claimMapper;
    }

    /** 查询自己的授权设置；未设置时返回 version=0、两个 false。 */
    public ContactPermissionView getMine(long userId, long claimId) {
        Map<String, Object> claim = requireParticipant(userId, claimId);
        Map<String, Object> row = mapper.find(claimId, userId);
        boolean effective = isEffective(claim);
        if (row == null) {
            return new ContactPermissionView(claimId, false, false, effective, 0L, null);
        }
        return new ContactPermissionView(
                claimId,
                Rows.bool(row, "phone_visible"),
                Rows.bool(row, "email_visible"),
                effective,
                Rows.num(row, "version"),
                Rows.time(row, "updated_at"));
    }

    /** 设置或撤回自己的授权；两字段都 false 表示撤回。 */
    @Transactional
    public ContactPermissionView setMine(long userId, long claimId, ContactPermissionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体不能为空");
        }
        if (request.expectedVersion() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "expectedVersion 不能为负数");
        }
        Map<String, Object> claim = requireParticipant(userId, claimId);
        if (!isEffective(claim)) {
            throw new BusinessException(ErrorCode.CONTACT_NOT_ACTIVE);
        }
        Map<String, Object> contact = mapper.findUserContact(userId);
        if (request.phoneVisible() && (contact == null || blank(Rows.str(contact, "phone")))) {
            throw new BusinessException(ErrorCode.CONTACT_FIELD_MISSING, "尚未填写手机号，不能授权手机号");
        }

        Map<String, Object> before = mapper.find(claimId, userId);
        Map<String, Object> row = new HashMap<>();
        row.put("claimId", claimId);
        row.put("grantorId", userId);
        row.put("phoneVisible", request.phoneVisible() ? 1 : 0);
        row.put("emailVisible", request.emailVisible() ? 1 : 0);
        row.put("expectedVersion", request.expectedVersion());

        if (request.expectedVersion() == 0) {
            if (before != null) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            }
            try {
                mapper.insert(row);
            } catch (DuplicateKeyException ex) {
                // 两个页面同时首次设置，只有一个能成功
                throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            }
        } else if (mapper.updateVersioned(row) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        writeLog(userId, claimId, "SET_CONTACT_PERMISSION",
                flagText(before),
                flagText(request.phoneVisible(), request.emailVisible()));
        return getMine(userId, claimId);
    }

    /** 查询对方已授权的联系方式；未授权字段不返回。 */
    public Map<String, Object> getCounterparty(long userId, long claimId) {
        Map<String, Object> claim = requireParticipant(userId, claimId);
        if (!isEffective(claim)) {
            throw new BusinessException(ErrorCode.CONTACT_NOT_ACTIVE);
        }
        long applicantId = Rows.num(claim, "applicant_id");
        long otherId = applicantId == userId ? Rows.num(claim, "publisher_id") : applicantId;
        Map<String, Object> permission = mapper.find(claimId, otherId);
        boolean phoneVisible = permission != null && Rows.bool(permission, "phone_visible");
        boolean emailVisible = permission != null && Rows.bool(permission, "email_visible");
        if (!phoneVisible && !emailVisible) {
            throw new BusinessException(ErrorCode.CONTACT_NOT_AUTHORIZED);
        }

        Map<String, Object> contact = mapper.findUserContact(otherId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("claimId", claimId);
        if (phoneVisible && contact != null && !blank(Rows.str(contact, "phone"))) {
            result.put("phone", Rows.str(contact, "phone"));
        }
        if (emailVisible && contact != null && !blank(Rows.str(contact, "email"))) {
            result.put("email", Rows.str(contact, "email"));
        }
        if (result.size() == 1) {
            // 授权开关还在，但对方删除了字段或字段为空，等同于没有可用联系方式
            throw new BusinessException(ErrorCode.CONTACT_NOT_AUTHORIZED);
        }
        return result;
    }

    /**
     * 用户改了手机号或邮箱后，撤回他在所有认领中对该字段的授权，防止对方静默看到新值。
     * 只撤回改动的那个字段，另一字段的授权不受影响。须在资料修改的同一事务中调用。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllForGrantor(long grantorId, ContactField field) {
        return field == ContactField.PHONE
                ? mapper.revokePhoneForGrantor(grantorId)
                : mapper.revokeEmailForGrantor(grantorId);
    }

    /** 交接完成或违规下架时清空该认领双方授权并递增版本，须在同一事务中调用。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllForClaim(long claimId) {
        return mapper.revokeAllForClaim(claimId);
    }

    /** 无关用户统一按不存在处理，避免探测认领关系。 */
    private Map<String, Object> requireParticipant(long userId, long claimId) {
        Map<String, Object> claim = claimMapper.findClaimContext(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        long applicantId = Rows.num(claim, "applicant_id");
        long publisherId = Rows.num(claim, "publisher_id");
        if (userId != applicantId && userId != publisherId) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        return claim;
    }

    private static boolean isEffective(Map<String, Object> claim) {
        return "CONFIRMED".equals(Rows.str(claim, "status"))
                && "PUBLISHED".equals(Rows.str(claim, "item_status"));
    }

    private void writeLog(long actorId, long claimId, String action, String before, String after) {
        Map<String, Object> log = new HashMap<>();
        log.put("actorId", actorId);
        log.put("entityType", "CONTACT_PERMISSION");
        log.put("entityId", claimId);
        log.put("action", action);
        log.put("beforeStatus", before);
        log.put("afterStatus", after);
        log.put("requestId", RequestIdFilter.current());
        claimMapper.insertOperationLog(log);
    }

    private static String flagText(Map<String, Object> permission) {
        if (permission == null) {
            return "NONE";
        }
        return flagText(Rows.bool(permission, "phone_visible"), Rows.bool(permission, "email_visible"));
    }

    private static String flagText(boolean phone, boolean email) {
        return (phone ? "P1" : "P0") + (email ? "E1" : "E0");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
