package cn.edu.scuec.icpc.lab.auth;

import cn.edu.scuec.icpc.lab.identity.UserAccount;
import cn.edu.scuec.icpc.lab.identity.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录失败计数必须独立提交（REQUIRES_NEW）：
 * 失败路径随后会抛出 401 使外层事务回滚，若与登录同事务则计数与锁定永久丢失。
 */
@Service
public class LoginAttemptService {

    private final UserAccountRepository accountRepository;

    public LoginAttemptService(UserAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(UUID accountId, int maxAttempts, Duration lockDuration) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("账号不存在：" + accountId));
        account.setFailedAttempts(account.getFailedAttempts() + 1);
        if (account.getFailedAttempts() >= maxAttempts) {
            account.setLockedUntil(Instant.now().plus(lockDuration));
        }
        accountRepository.save(account);
    }
}