package com.campus.lostfound.common.audit;

import com.campus.lostfound.common.web.RequestIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关键状态与授权变更的公共审计写入。
 *
 * <p>必须在业务事务内调用，与状态变化一起提交或回滚；只记录状态编码，
 * 不写密码、验证码、联系方式和留言正文。</p>
 */
@Service
public class AuditService {

    /** 与 operation_logs.ck_operation_entity 的取值一致。 */
    public enum Entity { USER, ITEM, CLAIM, CLUE, CONTACT_PERMISSION, REPORT }

    private final OperationLogMapper mapper;

    public AuditService(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param actorId 操作者；系统操作传 null
     * @param before  变更前状态，无则 null
     * @param after   变更后状态，无则 null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, Entity entity, long entityId, String action, String before, String after) {
        mapper.insert(actorId, entity.name(), entityId, action, before, after, RequestIdFilter.current());
    }
}
