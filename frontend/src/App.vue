<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import AlertToastList from './components/AlertToastList.vue'
import AppFooter from './components/AppFooter.vue'
import BetaBanner from './components/BetaBanner.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import SidebarNavigation from './components/SidebarNavigation.vue'

const route = useRoute()

useHead({
  titleTemplate: (title) => (title ? `${title} | CEEDS Reference Data` : 'CEEDS Reference Data'),
})
useHead(computed(() => (route.meta.title ? { title: route.meta.title } : {})))
</script>

<template>
  <ConfirmDialog />
  <AlertToastList />
  <BetaBanner />
  <div class="content-grid">
    <SidebarNavigation class="sticky sidebar" />
    <RouterView v-slot="{ Component }" class="main-default">
      <Transition mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
    <AppFooter />
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
  grid-template-rows: 1fr auto;
}

.main-default {
  min-width: 0;
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
