<script lang="ts" setup>
import { userRole } from '@/stores/userInfo'

const swaggerUrl = `${import.meta.env.VITE_BASE_URL}/swagger-ui.html`

const userRoleOptions = ['viewer', 'ceedsParticipant', 'ceedsEntitiy', 'dataspaceFacilitator']
</script>

<template>
  <aside class="sidebar-nav">
    <RouterLink to="/">
      <img src="/INSIEME_Logo.png" alt="logo" />
    </RouterLink>

    <nav>
      <RouterLink to="/"> Home </RouterLink>
      <a :href="swaggerUrl" target="_blank">API</a>
    </nav>

    <div>
      <label>
        logged in as:
        <select v-model="userRole" id="userSelect">
          <option v-for="role in userRoleOptions" :key="role">
            {{ role }}
          </option>
        </select>
      </label>
      <a>Sign Out</a>
    </div>
  </aside>
</template>
