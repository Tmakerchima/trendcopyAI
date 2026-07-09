package ai.gamecopy.usage;

import ai.gamecopy.auth.CurrentUser;
import ai.gamecopy.auth.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UsageService {
  private static final int FREE_DAILY_LIMIT = 3;

  private final UserRepository userRepository;

  public UsageService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public UsageStatus status(CurrentUser user) {
    if (user == null || !user.authenticated()) {
      return new UsageStatus(false, false, 0, FREE_DAILY_LIMIT, 0);
    }
    if (user.permanent()) {
      return new UsageStatus(true, true, 0, FREE_DAILY_LIMIT, Integer.MAX_VALUE);
    }

    int used = userRepository.todayUsageCount(user.email(), "generate");
    int remaining = Math.max(0, FREE_DAILY_LIMIT - used);
    return new UsageStatus(true, false, used, FREE_DAILY_LIMIT, remaining);
  }

  public void ensureCanGenerate(CurrentUser user) {
    UsageStatus status = status(user);
    if (!status.authenticated()) {
      throw new IllegalArgumentException("请先登录后再生成内容。");
    }
    if (!status.unlimited() && status.remaining() <= 0) {
      throw new IllegalArgumentException("今日免费次数已用完，请选择套餐继续生成。");
    }
  }

  public UsageStatus recordGenerate(CurrentUser user) {
    if (user != null && user.authenticated() && !user.permanent()) {
      userRepository.recordUsage(user, "generate");
    }
    return status(user);
  }
}
