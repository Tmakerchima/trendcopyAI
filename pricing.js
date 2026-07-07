const toast = document.querySelector("#toast");

function showToast(message) {
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 1800);
}

async function createAlipayOrder(planId) {
  const response = await fetch("/api/payments/alipay", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ planId }),
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || "支付服务暂时不可用");
  return payload;
}

document.querySelectorAll("[data-plan]").forEach((button) => {
  button.addEventListener("click", async () => {
    button.disabled = true;
    const originalText = button.textContent;
    button.textContent = "正在打开支付...";
    try {
      const payment = await createAlipayOrder(button.dataset.plan);
      const paymentWindow = window.open("", "_blank");
      if (!paymentWindow) {
        showToast("请允许浏览器打开支付窗口");
        return;
      }
      paymentWindow.document.open();
      paymentWindow.document.write(payment.paymentForm);
      paymentWindow.document.close();
    } catch (error) {
      showToast(error.message || "支付服务暂时不可用");
    } finally {
      button.disabled = false;
      button.textContent = originalText;
    }
  });
});
