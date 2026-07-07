const params = new URLSearchParams(window.location.search);
const errorBox = document.querySelector("#login-error");
const successBox = document.querySelector("#login-success");
const emailForm = document.querySelector("#email-form");
const codeForm = document.querySelector("#code-form");
const emailInput = document.querySelector("#email");
const codeInput = document.querySelector("#code");
const sendCodeButton = document.querySelector("#send-code-button");
const changeEmailButton = document.querySelector("#change-email-button");

function showError(message) {
  if (!errorBox) return;
  errorBox.textContent = message;
  errorBox.hidden = !message;
  if (successBox) successBox.hidden = true;
}

function showSuccess(message) {
  if (!successBox) return;
  successBox.textContent = message;
  successBox.hidden = !message;
  if (errorBox) errorBox.hidden = true;
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.error || "请求失败，请稍后再试。");
  }
  return payload;
}

if (errorBox && params.get("login") === "failed") {
  const reason = params.get("reason");
  showError(reason ? `Login failed: ${reason}` : "Login failed. Please try again.");
}

emailForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = emailInput.value.trim();
  if (!email) return;

  sendCodeButton.disabled = true;
  const originalText = sendCodeButton.textContent;
  sendCodeButton.textContent = "Sending...";
  try {
    const result = await postJson("/api/auth/email/request-code", { email, purpose: "login" });
    emailForm.hidden = true;
    codeForm.hidden = false;
    codeInput.value = "";
    codeInput.focus();
    showSuccess(result.message || "验证码已发送。");
  } catch (error) {
    showError(error.message);
  } finally {
    sendCodeButton.disabled = false;
    sendCodeButton.textContent = originalText;
  }
});

codeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = emailInput.value.trim();
  const code = codeInput.value.trim();
  if (!email || !code) return;

  const button = codeForm.querySelector(".email-button");
  button.disabled = true;
  const originalText = button.textContent;
  button.textContent = "验证中...";
  try {
    await postJson("/api/auth/email/verify", { email, code, purpose: "login" });
    window.location.href = "/?login=success&provider=email";
  } catch (error) {
    showError(error.message);
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
});

changeEmailButton.addEventListener("click", () => {
  codeForm.hidden = true;
  emailForm.hidden = false;
  codeInput.value = "";
  emailInput.focus();
  showError("");
  showSuccess("");
});
