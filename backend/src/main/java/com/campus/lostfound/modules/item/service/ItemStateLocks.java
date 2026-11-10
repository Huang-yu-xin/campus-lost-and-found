package com.campus.lostfound.modules.item.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.item.mapper.ItemStateMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 审核、认领、留言和授权写入都先锁物品，再锁认领并重读状态。 */
@Service
public class ItemStateLocks {
    private final ItemStateMapper mapper;

    public ItemStateLocks(ItemStateMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> lockItem(long itemId) {
        Map<String, Object> item = mapper.lockItem(itemId);
        if (item == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return item;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> lockClaim(long claimId) {
        Long itemId = mapper.findClaimItem(claimId);
        if (itemId == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Map<String, Object> item = lockItem(itemId);
        Map<String, Object> claim = mapper.lockClaim(claimId);
        if (claim == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        claim.put("item_status", item.get("status"));
        claim.put("item_type", item.get("type"));
        claim.put("item_accepted_claim_id", item.get("accepted_claim_id"));
        return claim;
    }
}
