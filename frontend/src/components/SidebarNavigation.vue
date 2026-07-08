<script lang="ts" setup>
import { userRole } from '@/stores/userInfo'

const swaggerUrl = `${import.meta.env.VITE_BASE_URL}/swagger-ui.html`

const userRoleOptions = ['viewer', 'ceedsParticipant', 'ceedsEntity', 'dataspaceFacilitator']
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
      <label class="account-label" for="userSelect">Logged in as:</label>
      <div class="select-wrapper">
        <select v-model="userRole" id="userSelect">
          <option v-for="role in userRoleOptions" :key="role">
            {{ role }}
          </option>
        </select>
      </div>
      <a class="sign-out">Sign Out</a>
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
  border-right: 1px solid #e4e4e4;
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
  border-top: 1px solid #e4e4e4;
}

.account-label {
  font-size: 0.85rem;
  color: var(--dark);
}

.select-wrapper {
  position: relative;
}

.select-wrapper::after {
  content: '›';
  position: absolute;
  top: 50%;
  right: var(--spacing-md);
  transform: translateY(-50%);
  color: var(--dark);
  font-size: 1.1rem;
  pointer-events: none;
}

#userSelect {
  width: 100%;
  box-sizing: border-box;
  appearance: none;
  padding: var(--spacing-sm) var(--spacing-xlg) var(--spacing-sm) var(--spacing-md);
  border: 1px solid #d0d0d0;
  border-radius: 1.25rem;
  background-color: var(--light);
  color: var(--dark);
  font-size: 0.95rem;
  text-transform: capitalize;
  cursor: pointer;
}

#userSelect:focus-visible {
  outline: none;
  border-color: var(--teal);
  box-shadow: 0 0 0 3px var(--teal-tint-bg);
}

.sign-out {
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
