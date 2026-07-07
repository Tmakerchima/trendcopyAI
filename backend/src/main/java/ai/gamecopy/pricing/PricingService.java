package ai.gamecopy.pricing;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
  private final List<PricingPlan> plans = List.of(
      new PricingPlan("free", "免费", 0, 3, "每天 3 次生成，适合偶尔追热点或先体验效果。"),
      new PricingPlan("starter_100", "轻量包", 990, 100, "100 次生成，不自动续费，适合稳定更新的内容创作者。"),
      new PricingPlan("pro_monthly", "月卡", 2900, 1000, "1000 次生成，适合每天追热点、多平台分发和批量改写。")
  );

  public List<PricingPlan> listPlans() {
    return plans;
  }

  public Optional<PricingPlan> findPlan(String id) {
    return plans.stream().filter(plan -> plan.id().equals(id)).findFirst();
  }
}
