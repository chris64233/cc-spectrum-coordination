package com.chris64233.spectrumcoordination.service;

import com.chris64233.spectrumcoordination.domain.CoordinationLock;
import com.chris64233.spectrumcoordination.domain.RuleVersion;
import com.chris64233.spectrumcoordination.repository.CoordinationLockRepository;
import com.chris64233.spectrumcoordination.repository.RuleVersionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 干扰规则版本管理。
 *
 * <p>激活新版本会在同一事务内停用旧版本（任意时刻最多一个生效版本）。基于旧版本
 * 提交、尚未批准的申请，在批准时自动按新生效版本重新校验。
 */
@Service
public class RuleVersionService {

    private final RuleVersionRepository ruleVersionRepository;
    private final CoordinationLockRepository coordinationLockRepository;

    public RuleVersionService(RuleVersionRepository ruleVersionRepository,
                              CoordinationLockRepository coordinationLockRepository) {
        this.ruleVersionRepository = ruleVersionRepository;
        this.coordinationLockRepository = coordinationLockRepository;
    }

    /** 服务启动时确保协调锁单行与首个默认规则版本存在。 */
    @Transactional
    public void ensureInitialized() {
        if (!coordinationLockRepository.existsById(CoordinationLock.SINGLETON_ID)) {
            coordinationLockRepository.save(new CoordinationLock(CoordinationLock.SINGLETON_ID));
        }
        if (ruleVersionRepository.findByActiveTrue().isEmpty()) {
            RuleVersion initial = ruleVersionRepository
                    .save(new RuleVersion("v1", 1000.0, 10.0, false));
            initial.activate();
        }
    }

    @Transactional
    public RuleVersion create(String version, double marginMeters, double distanceFactor) {
        if (ruleVersionRepository.findAll().stream().anyMatch(r -> r.getVersion().equals(version))) {
            throw new BusinessRuleException("规则版本已存在: " + version);
        }
        return ruleVersionRepository.save(
                new RuleVersion(version, marginMeters, distanceFactor, false));
    }

    /** 激活指定版本：持协调锁后停用旧版本、激活新版本，原子切换。 */
    @Transactional
    public RuleVersion activate(Long id) {
        coordinationLockRepository.acquireForCoordination();
        RuleVersion next = ruleVersionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("规则版本不存在: " + id));
        ruleVersionRepository.findByActiveTrue().ifPresent(old -> old.setActive(false));
        next.activate();
        return next;
    }

    @Transactional(readOnly = true)
    public RuleVersion current() {
        return ruleVersionRepository.findByActiveTrue()
                .orElseThrow(() -> new BusinessRuleException("当前没有生效的规则版本"));
    }

    @Transactional(readOnly = true)
    public List<RuleVersion> all() {
        return ruleVersionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public RuleVersion get(Long id) {
        return ruleVersionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("规则版本不存在: " + id));
    }
}
