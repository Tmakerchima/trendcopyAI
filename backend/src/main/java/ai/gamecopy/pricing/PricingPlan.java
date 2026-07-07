package ai.gamecopy.pricing;

public record PricingPlan(
    String id,
    String name,
    int priceFen,
    int credits,
    String description
) {
  public String priceLabel() {
    if (priceFen == 0) {
      return "0 元";
    }
    return (priceFen / 100) + "." + String.format("%02d", priceFen % 100) + " 元";
  }
}
