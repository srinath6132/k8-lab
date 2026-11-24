const API = "";
const TOKEN_KEY = "apnaKiranaToken";
const USER_KEY = "apnaKiranaUser";
const KIRANA_CATEGORIES = new Set(["Staples", "Beverages", "Dairy", "Snacks", "Personal care"]);
const currency = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });

const state = {
  products: [],
  category: "All",
  query: "",
  cart: { items: [], total: 0 },
  token: localStorage.getItem(TOKEN_KEY),
  user: localStorage.getItem(USER_KEY)
};

const productGrid = document.querySelector("#product-grid");
const categories = document.querySelector("#categories");
const emptyState = document.querySelector("#empty-state");
const cartDialog = document.querySelector("#cart-dialog");
const authDialog = document.querySelector("#auth-dialog");
let toastTimer;

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
}

function authHeaders() {
  return state.token ? { Authorization: `Bearer ${state.token}` } : {};
}

async function request(path, options = {}) {
  const headers = { ...authHeaders(), ...options.headers };
  if (!headers["X-Correlation-ID"]) {
    headers["X-Correlation-ID"] = `ui-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }
  if (options.body) headers["Content-Type"] = "application/json";
  const response = await fetch(`${API}${path}`, { ...options, headers });
  if (!response.ok) {
    let detail = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      detail = body.detail || body.message || detail;
    } catch { /* Keep the status message for non-JSON responses. */ }
    throw new Error(detail);
  }
  if (response.status === 204) return null;
  return response.json();
}

function showToast(message) {
  const toast = document.querySelector("#toast");
  toast.textContent = message;
  toast.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("visible"), 2600);
}

function renderCategories() {
  const options = ["All", ...new Set(state.products.map((product) => product.category))];
  categories.innerHTML = options.map((category) => `
    <button class="category-button${state.category === category ? " selected" : ""}"
      type="button" data-category="${escapeHtml(category)}">${escapeHtml(category === "All" ? "All items" : category)}</button>
  `).join("");
}

function visibleProducts() {
  const query = state.query.toLocaleLowerCase().trim();
  return state.products.filter((product) => {
    const categoryMatches = state.category === "All" || product.category === state.category;
    const searchText = `${product.name} ${product.description} ${product.category}`.toLocaleLowerCase();
    return categoryMatches && (!query || searchText.includes(query));
  });
}

function renderProducts() {
  const products = visibleProducts();
  document.querySelector("#item-count").textContent = `${products.length} ${products.length === 1 ? "item" : "items"}`;
  emptyState.hidden = products.length > 0;
  productGrid.innerHTML = products.map((product) => `
    <article class="product-card">
      <div class="product-image-wrap">
        <img class="product-image" src="${escapeHtml(product.imageUrl)}" alt="${escapeHtml(product.name)}"
          loading="lazy" onerror="this.src='/shopfront.svg';this.onerror=null">
        <span class="product-category">${escapeHtml(product.category)}</span>
      </div>
      <div class="product-info">
        <h3>${escapeHtml(product.name)}</h3>
        <p>${escapeHtml(product.description)}</p>
        <div class="product-bottom">
          <span class="price">${currency.format(product.price)}</span>
          <button class="button add-button" type="button" data-add="${escapeHtml(product.id)}">＋ Add</button>
        </div>
      </div>
    </article>
  `).join("");
}

function renderAccount() {
  const button = document.querySelector("#account-button");
  button.textContent = state.user ? `Hi, ${state.user.split("@")[0]}` : "Sign in";
}

function renderCart() {
  const count = state.cart.items.reduce((total, item) => total + item.quantity, 0);
  document.querySelector("#cart-count").textContent = count;
  const content = document.querySelector("#cart-content");
  if (!state.cart.items.length) {
    content.innerHTML = '<p class="loading">Your basket is empty. Add a few neighborhood favorites!</p>';
    return;
  }
  content.innerHTML = `
    <div class="cart-items">${state.cart.items.map((item) => `
      <article class="cart-item">
        <div><h3>${escapeHtml(item.name)}</h3><small>${currency.format(item.price)} each · Qty ${item.quantity}</small></div>
        <div class="cart-item-actions">
          <button type="button" data-increment="${escapeHtml(item.productId)}" aria-label="Add one ${escapeHtml(item.name)}">＋</button>
          <button type="button" data-remove="${escapeHtml(item.productId)}" aria-label="Remove ${escapeHtml(item.name)}">×</button>
        </div>
      </article>
    `).join("")}</div>
    <div class="cart-summary"><span>Basket total</span><span>${currency.format(state.cart.total)}</span></div>
    <div class="cart-footer">
      <button class="clear-cart" type="button" id="clear-cart">Clear basket</button>
      <button class="button button-primary" type="button" id="checkout-button">Looks good</button>
    </div>
  `;
}

async function loadCart() {
  if (!state.token) {
    state.cart = { items: [], total: 0 };
    renderCart();
    return;
  }
  try {
    state.cart = await request("/api/cart");
    renderCart();
  } catch (error) {
    state.cart = { items: [], total: 0 };
    renderCart();
    showToast(error.message);
  }
}

async function loadProducts() {
  productGrid.innerHTML = '<p class="loading">Getting the shop ready...</p>';
  try {
    const catalog = await request("/api/products");
    state.products = catalog.filter((product) => KIRANA_CATEGORIES.has(product.category));
    renderCategories();
    renderProducts();
  } catch (error) {
    productGrid.innerHTML = "";
    emptyState.hidden = false;
    emptyState.textContent = `Couldn't load the shop: ${error.message}`;
  }
}

async function addToCart(productId) {
  if (!state.token) {
    showToast("Sign in to add items to your basket.");
    showAuth("login");
    return;
  }
  try {
    state.cart = await request("/api/cart/items", {
      method: "POST",
      body: JSON.stringify({ productId, quantity: 1 })
    });
    renderCart();
    showToast("Added to your basket.");
  } catch (error) {
    showToast(error.message);
  }
}

function showAuth(mode = "login") {
  document.querySelector("#auth-message").textContent = "";
  setAuthMode(mode);
  authDialog.showModal();
}

function setAuthMode(mode) {
  const registering = mode === "register";
  document.querySelector("#auth-title").textContent = registering ? "Create your account" : "Sign in";
  document.querySelector("#auth-submit").textContent = registering ? "Create account" : "Sign in";
  document.querySelector("#name-field").hidden = !registering;
  document.querySelector('#name-field input').required = registering;
  document.querySelectorAll("[data-mode]").forEach((button) => {
    button.classList.toggle("selected", button.dataset.mode === mode);
  });
  document.querySelector('input[name="password"]').autocomplete = registering ? "new-password" : "current-password";
}

categories.addEventListener("click", (event) => {
  const button = event.target.closest("[data-category]");
  if (!button) return;
  state.category = button.dataset.category;
  renderCategories();
  renderProducts();
});

productGrid.addEventListener("click", (event) => {
  const button = event.target.closest("[data-add]");
  if (button) addToCart(button.dataset.add);
});

document.querySelector("#search-form").addEventListener("submit", (event) => {
  event.preventDefault();
  state.query = document.querySelector("#search-input").value;
  renderProducts();
  document.querySelector("#shop").scrollIntoView({ behavior: "smooth" });
});

document.querySelector("#account-button").addEventListener("click", () => {
  if (state.token) {
    state.token = null;
    state.user = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    state.cart = { items: [], total: 0 };
    renderAccount();
    renderCart();
    showToast("You have signed out.");
  } else {
    showAuth("login");
  }
});

document.querySelector("#cart-button").addEventListener("click", async () => {
  if (!state.token) {
    showAuth("login");
    showToast("Sign in to see your basket.");
    return;
  }
  await loadCart();
  cartDialog.showModal();
});

document.querySelectorAll("[data-close]").forEach((button) => {
  button.addEventListener("click", () => document.querySelector(`#${button.dataset.close}`).close());
});

document.querySelectorAll("[data-mode]").forEach((button) => {
  button.addEventListener("click", () => setAuthMode(button.dataset.mode));
});

document.querySelector("#auth-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const data = Object.fromEntries(new FormData(form));
  const registering = !document.querySelector("#name-field").hidden;
  const message = document.querySelector("#auth-message");
  message.textContent = "";
  try {
    const session = await request(`/api/auth/${registering ? "register" : "login"}`, {
      method: "POST",
      body: JSON.stringify(data)
    });
    state.token = session.token;
    state.user = session.user.email;
    localStorage.setItem(TOKEN_KEY, state.token);
    localStorage.setItem(USER_KEY, state.user);
    renderAccount();
    await loadCart();
    authDialog.close();
    form.reset();
    showToast(`Welcome${registering ? " to Apna Kirana" : " back"}!`);
  } catch (error) {
    message.textContent = error.message;
  }
});

document.querySelector("#cart-content").addEventListener("click", async (event) => {
  const increment = event.target.closest("[data-increment]");
  const remove = event.target.closest("[data-remove]");
  try {
    if (increment) {
      state.cart = await request("/api/cart/items", {
        method: "POST",
        body: JSON.stringify({ productId: increment.dataset.increment, quantity: 1 })
      });
    } else if (remove) {
      state.cart = await request(`/api/cart/items/${encodeURIComponent(remove.dataset.remove)}`, { method: "DELETE" });
    } else if (event.target.closest("#clear-cart")) {
      state.cart = await request("/api/cart", { method: "DELETE" });
    } else if (event.target.closest("#checkout-button")) {
      showToast("Thanks! Online checkout is not enabled in this demo.");
      return;
    } else {
      return;
    }
    renderCart();
  } catch (error) {
    showToast(error.message);
  }
});

renderAccount();
renderCart();
loadProducts();
if (state.token) loadCart();
