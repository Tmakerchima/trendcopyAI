const params = new URLSearchParams(window.location.search);
const errorBox = document.querySelector("#login-error");

if (errorBox && params.get("login") === "failed") {
  const reason = params.get("reason");
  errorBox.hidden = false;
  errorBox.textContent = reason ? `Login failed: ${reason}` : "Login failed. Please try again.";
}
