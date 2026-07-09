const examples = [
  {
    game: "自动检索",
    platform: "小红书",
    topic: "Zackriya-Solutions/meetily：本地 AI 会议记录和总结工具",
    keywords: "AI 工具、独立开发者、本地隐私、效率工具",
    style: "小红书种草",
    notes: "",
  },
  {
    game: "自动检索",
    platform: "X / Twitter",
    topic: "一个开源 agent 框架突然被很多开发者讨论",
    keywords: "AI agent、自动化、开发者工具、workflow",
    style: "短观点",
    notes: "",
  },
  {
    game: "自动检索",
    platform: "Newsletter",
    topic: "新的 AI 浏览器自动化产品上线",
    keywords: "浏览器 agent、效率工具、SaaS、自动化",
    style: "理性简报",
    notes: "",
  },
  {
    game: "自动检索",
    platform: "小红书",
    topic: "大模型工具开始从聊天框走向可执行工作流",
    keywords: "AI workflow、MCP、插件、生产力",
    style: "趋势观察",
    notes: "",
  },
];

const platformStyles = {
  "小红书": ["小红书种草", "问题解决", "清单收藏", "避坑提醒", "趋势观察"],
  "X / Twitter": ["短观点", "连续线程", "反常识观点", "Build in public", "冷静分析"],
  "Newsletter": ["理性简报", "创始人视角", "市场地图", "链接摘要", "深度分析"],
};

let quota = 3;
let exampleIndex = 0;
let latest = {};
let currentValues = {};
let currentUserAuthenticated = false;
let currentUserPermanent = false;
let currentUsage = null;
let selectedTitleIndex = 0;
let selectedHookIndex = 0;
let selectedTagIndexes = new Set([0, 1, 2, 3, 4]);

const form = document.querySelector("#generator-form");
const toast = document.querySelector("#toast");
const generateButton = document.querySelector(".generate-button");

function normalizePreviewDom() {
  const panel = document.querySelector(".xhs-publish-panel");
  if (!panel) return;
  panel.id = "platform-preview-panel";

  const heading = panel.querySelector(".xhs-panel-head span");
  if (heading) heading.id = "platform-preview-title";

  const copyButton = document.querySelector("#copy-xhs-post");
  if (copyButton) copyButton.id = "copy-platform-post";

  const body = document.querySelector(".xhs-preview-card");
  if (body) {
    body.id = "platform-preview-body";
    body.innerHTML = "";
  }

  const openButton = document.querySelector("#open-xhs-creator");
  if (openButton) openButton.id = "open-platform-creator";
}

function getValues() {
  return {
    game: document.querySelector("#game").value,
    platform: document.querySelector("#platform").value,
    topic: document.querySelector("#topic").value.trim(),
    keywords: document.querySelector("#keywords").value.trim(),
    style: document.querySelector("#style").value,
    notes: document.querySelector("#notes").value.trim(),
  };
}

function fallbackCopy(values) {
  const source = "自动检索";
  const platform = values.platform || "小红书";
  const core = values.topic || "今天值得关注的 AI 项目";
  const keyword = values.keywords.split(/[，、,\s]+/).filter(Boolean)[0] || "AI 工具";

  return {
    titles: [
      `今天这个 ${source} 项目，可能是 ${keyword} 的新机会`,
      `别只收藏链接：${core} 可以这样做成内容`,
      `${platform} 可以发的 AI 热点：${core}`,
      "为什么这个项目会火？我拆了 3 个内容角度",
      `从 ${source} 到可发布内容：${keyword} 选题怎么写`,
    ],
    covers: ["今天就能发", "项目为什么火", "别只收藏", "3 个角度", "普通人怎么用"],
    description: `围绕“${core}”提炼内容角度，适合发布到 ${platform}。重点是讲清楚它为什么值得关注、谁会用、能带来什么启发。`,
    tags: [source, platform, "AI工具", "GitHubTrending", "内容选题", "独立开发", "效率工具", keyword],
    script: [
      `今天刷到一个值得关注的趋势：${core}。`,
      `它踩中了 ${keyword} 这个方向，而且用户能立刻理解它解决了什么问题。`,
      `在 ${platform} 上，不要只介绍功能，要把“谁需要它、为什么现在火、普通人怎么用”讲清楚。`,
      "如果你每天都不知道发什么，先从趋势项目里找真实需求，再把它翻译成用户听得懂的内容。",
    ],
    sources: sourcePaths(values, { sources: [] }),
    imageUrl: "",
  };
}

async function generateCopy(values) {
  const response = await fetch("/api/generate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(values),
  });

  const payload = await response.json();
  if (!response.ok) {
    const error = new Error(payload.error || "生成服务当前不可用");
    error.status = response.status;
    throw error;
  }
  return payload;
}

async function createAlipayOrder(planId) {
  const response = await fetch("/api/payments/alipay", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ planId }),
  });

  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.error || "支付服务暂时不可用");
  }
  return payload;
}

function renderList(selector, items, type = "li") {
  const target = document.querySelector(selector);
  target.innerHTML = "";
  items.forEach((item, index) => {
    const element = document.createElement(type);
    element.textContent = item;
    element.dataset.index = String(index);
    element.classList.add("selectable-item");
    target.appendChild(element);
  });
}

function renderScript(items) {
  const target = document.querySelector("#script");
  target.innerHTML = "";
  items.forEach((item) => {
    const element = document.createElement("p");
    element.textContent = item;
    target.appendChild(element);
  });
}

function sourcePaths(values, copy = latest) {
  if (Array.isArray(copy?.sources) && copy.sources.length) {
    return copy.sources;
  }
  const text = [values.game, values.topic, values.notes].filter(Boolean).join(" ");
  const urls = [...text.matchAll(/https?:\/\/[^\s"'<>，。)）]+/gi)].map((match) => match[0]);
  const uniqueUrls = [...new Set(urls)];
  if (uniqueUrls.length) {
    return uniqueUrls.map((url) => ({ label: url, title: url, url, excerpt: "", imageUrl: "" }));
  }
  const label = `${values.game || "手动输入"} / ${values.topic || "未填写趋势主题"}`;
  return [{ label, title: label, url: "", excerpt: "", imageUrl: "" }];
}

function renderSourcePaths(values, copy = latest) {
  const target = document.querySelector("#source-paths");
  target.innerHTML = "";
  sourcePaths(values, copy).forEach((source) => {
    const item = document.createElement(source.url ? "a" : "p");
    item.textContent = source.label || source.title || source.url;
    if (source.url) {
      item.href = source.url;
      item.target = "_blank";
      item.rel = "noopener noreferrer";
      item.title = source.url;
    }
    item.className = "source-path";
    target.appendChild(item);
  });
}

function render(copy, values) {
  latest = copy;
  currentValues = values;
  selectedTitleIndex = 0;
  selectedHookIndex = 0;
  selectedTagIndexes = new Set((copy.tags || []).slice(0, 5).map((_, index) => index));
  renderList("#titles", copy.titles || []);
  renderList("#tags", copy.tags || []);
  renderSourcePaths(values, copy);
  document.querySelector("#description").textContent = copy.description || "";
  renderScript(copy.script || []);
  bindSelectionControls();
  updatePlatformPreview();
}

function normalizeTag(tag) {
  return String(tag || "").replace(/^#+/, "").replace(/\s+/g, "").trim();
}

function selectedTags() {
  return (latest.tags || []).filter((_, index) => selectedTagIndexes.has(index));
}

function tagLine() {
  return selectedTags().map(normalizeTag).filter(Boolean).map((tag) => `#${tag}`).join(" ");
}

function selectedTitle() {
  return latest.titles?.[selectedTitleIndex] || "选择一个内容选题";
}

function selectedHook() {
  return latest.covers?.[selectedHookIndex] || "选择封面短句";
}

function bodyText() {
  return [latest.description || "", ...(latest.script || [])].filter(Boolean).join("\n\n");
}

function postText() {
  return [selectedTitle(), bodyText(), tagLine()].filter(Boolean).join("\n\n");
}

function githubRepoFromTopic(topic) {
  const match = String(topic || "").match(/\b([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+)\b/);
  return match ? `${match[1]}/${match[2]}` : "";
}

function trendImageUrl(values) {
  const params = new URLSearchParams();
  params.set("source", values?.game || "");
  params.set("platform", values?.platform || "");
  params.set("topic", values?.topic || "");
  params.set("keywords", values?.keywords || "");
  params.set("notes", values?.notes || "");
  if (latest?.imageUrl) {
    params.set("imageUrl", latest.imageUrl);
  }
  return `/api/trends/image?${params.toString()}`;
}

function platformKind() {
  const platform = currentValues.platform || document.querySelector("#platform").value;
  if (platform.includes("Twitter") || platform.includes("X /")) return "x";
  if (platform.includes("Newsletter")) return "newsletter";
  return "xhs";
}

function platformMeta() {
  const kind = platformKind();
  const map = {
    xhs: { label: "小红书预发布", cta: "打开小红书创作平台", url: "https://creator.xiaohongshu.com/" },
    x: { label: "X / Twitter 预览", cta: "打开 X", url: "https://x.com/compose/post" },
    newsletter: { label: "Newsletter 预览", cta: "复制到 Newsletter", url: "" },
  };
  return map[kind];
}

function primaryImageSourceUrl() {
  if (!Array.isArray(latest?.sources) || !latest.sources.length) return "";
  if (latest.imageUrl) {
    const matched = latest.sources.find((source) => source.imageUrl === latest.imageUrl);
    if (matched?.url) return matched.url;
  }
  return latest.sources[0]?.url || "";
}

function linkedPreviewImage(className, text) {
  const sourceUrl = primaryImageSourceUrl();
  const image = `<div class="${className}" ${previewImageStyle()}><span>${text}</span></div>`;
  return sourceUrl
    ? `<a class="preview-image-link" href="${sourceUrl}" target="_blank" rel="noopener noreferrer">${image}</a>`
    : image;
}

function previewImageStyle() {
  const imageUrl = trendImageUrl(currentValues);
  return `style="background-image: linear-gradient(180deg, rgba(21, 18, 15, 0.04), rgba(21, 18, 15, 0.52)), url('${imageUrl}')"`;
}

function renderPreviewCard(kind) {
  const title = selectedTitle();
  const hook = selectedHook();
  const body = bodyText();
  const tags = tagLine();

  if (kind === "x") {
    return `
      <div class="platform-card platform-card--x">
        <div class="tweet-head"><span class="avatar">T</span><div><strong>TrendCopy</strong><small>@trendcopy_ai</small></div></div>
        ${linkedPreviewImage("tweet-image", hook)}
        <h3>${title}</h3>
        <p>${body}</p>
        <p class="platform-tags">${tags}</p>
      </div>
    `;
  }

  if (kind === "newsletter") {
    return `
      <div class="platform-card platform-card--newsletter">
        <small>Subject</small>
        <h3>${title}</h3>
        ${linkedPreviewImage("newsletter-hero", hook)}
        <p>${body}</p>
      </div>
    `;
  }

  return `
    <div class="platform-card platform-card--xhs">
      ${linkedPreviewImage("xhs-cover has-image", hook)}
      <h3>${title}</h3>
      <p>${body}</p>
      <div class="xhs-tags">${tags}</div>
    </div>
  `;
}

function updatePlatformPreview() {
  const kind = platformKind();
  const meta = platformMeta();
  const panel = document.querySelector("#platform-preview-panel");
  const title = document.querySelector("#platform-preview-title");
  const body = document.querySelector("#platform-preview-body");
  const openButton = document.querySelector("#open-platform-creator");

  panel.dataset.platform = kind;
  title.textContent = meta.label;
  openButton.textContent = meta.cta;
  openButton.dataset.url = meta.url;
  body.innerHTML = renderPreviewCard(kind);
}

function markSelections() {
  document.querySelectorAll("#titles li").forEach((item) => {
    item.classList.toggle("selected", Number(item.dataset.index) === selectedTitleIndex);
  });
  document.querySelectorAll("#tags li").forEach((item) => {
    item.classList.toggle("selected", selectedTagIndexes.has(Number(item.dataset.index)));
  });
}

function bindSelectionControls() {
  document.querySelectorAll("#titles li").forEach((item) => {
    item.addEventListener("click", () => {
      selectedTitleIndex = Number(item.dataset.index);
      markSelections();
      updatePlatformPreview();
    });
  });

  document.querySelectorAll("#tags li").forEach((item) => {
    item.addEventListener("click", () => {
      const index = Number(item.dataset.index);
      if (selectedTagIndexes.has(index)) selectedTagIndexes.delete(index);
      else selectedTagIndexes.add(index);
      markSelections();
      updatePlatformPreview();
    });
  });

  markSelections();
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 1800);
}

function ensureUsageModule() {
  let usage = document.querySelector("#usage-module");
  if (usage) return usage;

  usage = document.createElement("div");
  usage.id = "usage-module";
  usage.className = "usage-module";
  usage.innerHTML = `
    <strong>Usage:<span id="quota-count">3</span>/3</strong>
  `;
  document.querySelector(".header-left")?.appendChild(usage);
  return usage;
}

function updateQuota() {
  ensureUsageModule();
  const quotaCount = document.querySelector("#quota-count");
  if (currentUserPermanent) {
    quota = Number.POSITIVE_INFINITY;
    quotaCount.textContent = "∞";
    return;
  }
  if (currentUsage) {
    quota = currentUsage.remaining;
    quotaCount.textContent = String(currentUsage.remaining);
    return;
  }
  quota = currentUserAuthenticated ? 3 : 0;
  quotaCount.textContent = String(quota);
}

function applyUsage(usage) {
  if (!usage) return;
  currentUsage = usage;
  currentUserAuthenticated = Boolean(usage.authenticated);
  currentUserPermanent = Boolean(usage.unlimited);
  updateQuota();
}

async function refreshUsage() {
  try {
    const response = await fetch("/api/usage");
    if (!response.ok) return;
    applyUsage(await response.json());
  } catch (error) {
    console.error(error);
  }
}

function updateStyleOptions(preferredValue = "") {
  const platform = document.querySelector("#platform").value;
  const styleSelect = document.querySelector("#style");
  const styles = platformStyles[platform] || platformStyles["小红书"];
  styleSelect.innerHTML = "";
  styles.forEach((style) => {
    const option = document.createElement("option");
    option.value = style;
    option.textContent = style;
    styleSelect.appendChild(option);
  });
  if (preferredValue && styles.includes(preferredValue)) {
    styleSelect.value = preferredValue;
  }
}

function resizeAutoGrowField(field) {
  if (!field) return;
  field.style.height = "auto";
  field.style.height = `${field.scrollHeight}px`;
}

function refreshAutoGrowFields() {
  document.querySelectorAll("textarea.auto-grow").forEach(resizeAutoGrowField);
}

function fillExample(example) {
  document.querySelector("#game").value = example.game;
  document.querySelector("#platform").value = example.platform;
  updateStyleOptions(example.style);
  document.querySelector("#topic").value = example.topic;
  document.querySelector("#keywords").value = example.keywords;
  document.querySelector("#notes").value = example.notes;
  refreshAutoGrowFields();
}

function copyText(key) {
  const map = {
    titles: latest.titles?.map((item, index) => `${index + 1}. ${item}`).join("\n"),
    description: latest.description,
    tags: latest.tags?.join(" "),
    sources: sourcePaths(currentValues).map((source) => source.url || source.label).join("\n"),
    script: latest.script?.join("\n"),
  };

  const text = map[key] || "";
  if (!text) {
    showToast("暂无可复制内容");
    return;
  }
  navigator.clipboard.writeText(text).then(() => showToast("已复制"));
}

function ensureAccountMenu() {
  const authMenu = document.querySelector(".auth-menu");
  if (!authMenu) return null;

  let menu = document.querySelector("#account-menu");
  if (!menu) {
    menu = document.createElement("div");
    menu.className = "account-menu";
    menu.id = "account-menu";
    menu.hidden = true;
    menu.innerHTML = `
      <div class="account-menu__name" id="account-name"></div>
      <div class="account-menu__meta" id="account-meta"></div>
      <button type="button" id="logout-button">退出登录</button>
    `;
    authMenu.appendChild(menu);
  }
  return menu;
}

function displayNameForUser(user) {
  return user.name || user.provider || "已登录";
}

async function refreshCurrentUser() {
  const authLink = document.querySelector("#auth-link");
  if (!authLink) return;
  const menu = ensureAccountMenu();

  try {
    const response = await fetch("/api/auth/me");
    const user = await response.json();
    if (!user.authenticated) {
      currentUserAuthenticated = false;
      currentUserPermanent = false;
      currentUsage = null;
      authLink.textContent = "登录";
      authLink.href = "/dashbord";
      authLink.classList.remove("signed-in");
      authLink.onclick = null;
      if (menu) menu.hidden = true;
      updateQuota();
      return;
    }

    const displayName = displayNameForUser(user);
    currentUserAuthenticated = true;
    currentUserPermanent = Boolean(user.permanent);
    authLink.textContent = displayName;
    authLink.href = "#";
    authLink.classList.add("signed-in");
    authLink.title = displayName;
    document.querySelector("#account-name").textContent = displayName;
    document.querySelector("#account-meta").textContent = user.permanent
      ? "永久账号"
      : `${user.provider || "OAuth"} 登录`;
    authLink.onclick = (event) => {
      event.preventDefault();
      event.stopPropagation();
      menu.hidden = !menu.hidden;
    };
    menu.onclick = (event) => event.stopPropagation();
    document.querySelector("#logout-button").onclick = async () => {
      await fetch("/api/auth/logout", { method: "POST" });
      window.location.href = "/";
    };
    document.addEventListener("click", (event) => {
      if (!document.querySelector(".auth-menu")?.contains(event.target)) menu.hidden = true;
    });
    await refreshUsage();
  } catch (error) {
    console.error(error);
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!currentUserAuthenticated) {
    showToast("请先登录后再生成内容");
    window.location.href = "/dashbord";
    return;
  }
  if (!currentUserPermanent && currentUsage && currentUsage.remaining <= 0) {
    showToast("今日免费次数已用完，请选择套餐继续生成");
    return;
  }

  const values = getValues();
  generateButton.disabled = true;
  generateButton.textContent = "生成中...";
  try {
    const copy = await generateCopy(values);
    applyUsage(copy.usage);
    render(copy, values);
    showToast("已生成内容");
  } catch (error) {
    console.error(error);
    if (![400, 401, 403].includes(error.status)) {
      render(fallbackCopy(values), values);
    }
    showToast(error.message || "生成服务当前不可用");
  } finally {
    generateButton.disabled = false;
    generateButton.textContent = "生成内容";
  }
});

document.querySelector("#reset-button").addEventListener("click", () => {
  exampleIndex = (exampleIndex + 1) % examples.length;
  fillExample(examples[exampleIndex]);
  const values = getValues();
  render(fallbackCopy(values), values);
  showToast("已切换趋势");
});

document.querySelector("#platform").addEventListener("change", () => {
  updateStyleOptions();
  currentValues = getValues();
  updatePlatformPreview();
});

document.querySelector("#game")?.addEventListener("input", () => {
  currentValues = getValues();
  renderSourcePaths(currentValues);
  updatePlatformPreview();
});

document.querySelectorAll("textarea.auto-grow").forEach((field) => {
  field.addEventListener("input", () => resizeAutoGrowField(field));
});

document.querySelectorAll("[data-copy]").forEach((button) => {
  button.addEventListener("click", () => copyText(button.dataset.copy));
});

document.addEventListener("click", (event) => {
  if (event.target.matches("#copy-xhs-post, #copy-platform-post")) {
    const text = postText();
    if (!text) {
      showToast("暂无可复制内容");
      return;
    }
    navigator.clipboard.writeText(text).then(() => showToast("已复制全文"));
  }

  if (event.target.matches("#open-xhs-creator, #open-platform-creator")) {
    const url = event.target.dataset.url || "https://creator.xiaohongshu.com/";
    if (url) window.open(url, "_blank", "noopener,noreferrer");
  }
});

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
      console.error(error);
      showToast(error.message || "支付服务暂时不可用");
    } finally {
      button.disabled = false;
      button.textContent = originalText;
    }
  });
});

const params = new URLSearchParams(window.location.search);
if (params.get("login") === "success") {
  showToast("登录成功");
  window.history.replaceState({}, document.title, window.location.pathname + window.location.hash);
}

normalizePreviewDom();
ensureUsageModule();
fillExample(examples[0]);
render(fallbackCopy(getValues()), getValues());
updateQuota();
refreshCurrentUser();
