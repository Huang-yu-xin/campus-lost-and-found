package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.modules.account.mapper.MockStudentMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 虚拟名册核对：学号、姓名、校园卡号三项必须与一条启用的名册记录完全一致。
 * 名册只读，只证明“这组身份存在”，不冒充真实的学校认证。
 */
@Service
public class RosterService {

    private final MockStudentMapper mockStudentMapper;

    public RosterService(MockStudentMapper mockStudentMapper) {
        this.mockStudentMapper = mockStudentMapper;
    }

    /** 匹配成功返回名册记录 id。 */
    public Optional<Long> findMatchingStudent(String studentNo, String realName, String campusCardNo) {
        return Optional.ofNullable(mockStudentMapper.findEnabledMatch(studentNo, realName, campusCardNo));
    }
}
