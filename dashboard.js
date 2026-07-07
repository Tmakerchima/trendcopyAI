const params = new URLSearchParams(window.location.search);

const errorBox = document.querySelector("#login-error");
const successBox = document.querySelector("#login-success");
const emailStep = document.querySelector("#email-step");
const emailForm = document.querySelector("#email-form");
const codeForm = document.querySelector("#code-form");
const passwordForm = document.querySelector("#password-form");
const emailInput = document.querySelector("#email");
const codeInput = document.querySelector("#code");
const passwordInput = document.querySelector("#password");
const continueEmailButton = document.querySelector("#continue-email-button");
const codeCopy = document.querySelector("#code-copy");
const passwordTitle = document.querySelector("#password-title");
const passwordCopy = document.querySelector("#password-copy");
const passwordButton = document.querySelector("#password-button");
const backToEmailFromCode = document.querySelector("#back-to-email-from-code");
const backToEmailFromPassword = document.querySelector("#back-to-email-from-password");

const state = {
  email: "",
  mode: "login",
};

function showError(message) {
  if (!errorBox) return;
  errorBox.textContent = message;
  errorBox.hidden = !message;
  if (successBox && message) successBox.hidden = true;
}

function showSuccess(message) {
  if (!successBox) return;
  successBox.textContent = message;
  successBox.hidden = !message;
  if (errorBox && message) errorBox.hidden = true;
}

function showStep(step) {
  emailStep.hidden = step !== "email";
  codeForm.hidden = step !== "code";
  passwordForm.hidden = step !== "password";
}

function resetMessages() {
  showError("");
  showSuccess("");
}

function setBusy(button, busy, busyText) {
  if (!button) return () => {};
  const originalText = button.textContent;
  button.disabled = busy;
  if (busyText) button.textContent = busyText;
  return () => {
    button.disabled = false;
    button.textContent = originalText;
  };
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.error || "请求失败，请稍后再试。");
  }
  return payload;
}

function configurePasswordStep(mode) {
  state.mode = mode;
  const isRegister = mode === "register";
  passwordTitle.textContent = isRegister ? "Create your password" : "Enter your password";
  passwordCopy.textContent = isRegister
    ? `为 ${state.email} 设置一个至少 8 位的密码。`
    : `继续登录 ${state.email}`;
  passwordButton.textContent = isRegister ? "Create account" : "Continue";
  passwordInput.value = "";
  passwordInput.autocomplete = isRegister ? "new-password" : "current-password";
  showStep("password");
  passwordInput.focus();
}

if (errorBox && params.get("login") === "failed") {
  const reason = params.get("reason");
  showError(reason ? `Login failed: ${reason}` : "Login failed. Please try again.");
}

emailForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  resetMessages();
  const email = emailInput.value.trim().toLowerCase();
  if (!email) return;

  const clearBusy = setBusy(continueEmailButton, true, "Checking...");
  try {
    const result = await postJson("/api/auth/email/start", { email });
    state.email = email;
    if (result.registered) {
      configurePasswordStep("login");
      return;
    }

    codeCopy.textContent = `我们已向 ${email} 发送 6 位验证码。`;
    codeInput.value = "";
    showSuccess(result.message || "验证码已发送。");
    showStep("code");
    codeInput.focus();
  } catch (error) {
    showError(error.message);
  } finally {
    clearBusy();
  }
});

codeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  resetMessages();
  const code = codeInput.value.trim();
  if (!state.email || !code) return;

  const button = codeForm.querySelector(".email-button");
  const clearBusy = setBusy(button, true, "Verifying...");
  try {
    await postJson("/api/auth/email/verify-registration", {
      email: state.email,
      code,
      purpose: "register",
    });
    configurePasswordStep("register");
  } catch (error) {
    showError(error.message);
  } finally {
    clearBusy();
  }
});

passwordForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  resetMessages();
  const password = passwordInput.value;
  if (!state.email || !password) return;

  const clearBusy = setBusy(passwordButton, true, state.mode === "register" ? "Creating..." : "Signing in...");
  try {
    const endpoint = state.mode === "register"
      ? "/api/auth/email/set-password"
      : "/api/auth/email/password-login";
    await postJson(endpoint, { email: state.email, password });
    window.location.href = "/?login=success&provider=email";
  } catch (error) {
    showError(error.message);
  } finally {
    clearBusy();
  }
});

function backToEmail() {
  state.email = "";
  state.mode = "login";
  codeInput.value = "";
  passwordInput.value = "";
  showStep("email");
  resetMessages();
  emailInput.focus();
}

backToEmailFromCode.addEventListener("click", backToEmail);
backToEmailFromPassword.addEventListener("click", backToEmail);
