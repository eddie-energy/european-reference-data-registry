<script lang="ts" setup>
import { computed } from 'vue'
import { userRole, username } from '@/stores/userInfo'
import { isAuthenticated, login, logout, register } from '@/keycloak'

const swaggerUrl = `${import.meta.env.VITE_BASE_URL}/swagger-ui.html`

const ROLE_LABELS = {
  viewer: 'Viewer',
  participant: 'Participant',
  ndsf: 'NDSF',
  operationalEntity: 'Operational Entity',
}

const authenticated = computed(() => isAuthenticated())
const roleLabel = computed(() => ROLE_LABELS[userRole.value])
</script>

<template>
  <aside class="sidebar-nav">
    <RouterLink to="/" class="logo">
      <img src="/INSIEME_Logo.png" alt="logo" />
    </RouterLink>

    <nav>
      <RouterLink to="/"> Home </RouterLink>
      <a :href="swaggerUrl" target="_blank">API</a>
    </nav>

    <div class="account">
      <template v-if="authenticated">
        <span class="account-label">Signed in as</span>
        <span class="account-name">{{ username }}</span>
        <span class="account-role">{{ roleLabel }}</span>
        <button type="button" class="sign-out" @click="logout">Sign Out</button>
      </template>
      <template v-else>
        <span class="account-label">Browsing as {{ roleLabel }}</span>
        <button type="button" class="sign-in" @click="login">Sign In</button>
        <button type="button" class="sign-out" @click="register">Create Account</button>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-nav {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  width: 15rem;
  padding: var(--spacing-xlg) var(--spacing-lg);
  background-color: var(--light);
  border-right: 1px solid var(--border-color);
}

.logo {
  margin-bottom: var(--spacing-xxl);
}

.logo img {
  display: block;
  height: 2.25rem;
}

nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

nav a {
  display: block;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--pill-radius);
  color: var(--dark);
  text-decoration: none;
  font-size: 1rem;
  font-weight: 500;
  transition: var(--theme-transition);
}

nav a:hover {
  background-color: var(--teal-tint-bg);
  color: var(--teal-tint-text);
}

nav a.router-link-active {
  background-color: var(--teal);
  color: var(--light);
  font-weight: 600;
}

.account {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--border-color);
}

.account-label {
  font-size: 0.85rem;
  color: var(--dark);
}

.account-name {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--dark);
}

.account-role {
  font-size: 0.85rem;
  color: var(--dark);
  opacity: 0.7;
}

.sign-in {
  box-sizing: border-box;
  width: 100%;
  padding: var(--spacing-sm) var(--spacing-md);
  border: none;
  border-radius: var(--pill-radius);
  background-color: var(--teal);
  color: var(--light);
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: var(--theme-transition);
}

.sign-in:hover {
  background-color: var(--teal-tint-text);
}

.sign-in:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--teal-tint-bg);
}

.sign-out {
  background: none;
  border: none;
  padding: 0;
  text-align: left;
  font-family: inherit;
  color: var(--dark);
  opacity: 0.7;
  text-decoration: none;
  font-size: 0.9rem;
  cursor: pointer;
  transition: var(--theme-transition);
}

.sign-out:hover {
  color: var(--error);
  opacity: 1;
}
</style>
