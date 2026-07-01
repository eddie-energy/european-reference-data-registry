<script setup lang="ts">
import SidebarNavigation from './components/SidebarNavigation.vue'
</script>

<template>
  <div class="content-grid">
    <SidebarNavigation class="sticky sidebar" />
    <RouterView v-slot="{ Component }" class="main-default">
      <Transition mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </div>
</template>

<style scoped>
.v-enter-active,
.v-leave-active {
  transition: opacity 0.3s ease;
}

.v-enter-from,
.v-leave-to {
  opacity: 0;
}

.content-grid {
  display: grid;
  grid-template-columns: auto 1fr;
}

.sidebar {
  grid-row: span 2;
}

.sticky {
  position: sticky;
  top: 0;
  z-index: 3;
}
</style>
