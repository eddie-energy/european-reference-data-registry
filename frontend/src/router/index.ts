import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import ReferenceDataObjectView from '@/views/ReferenceDataObjectView.vue'
import CreateReferenceDataObjectView from '@/views/CreateReferenceDataObjectView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
    },
    {
      path: '/reference-data-objects/create',
      name: 'reference-data-object-create',
      component: CreateReferenceDataObjectView,
    },
    {
      path: '/reference-data-objects/:id',
      name: 'reference-data-object',
      component: ReferenceDataObjectView,
      props: true,
    },
  ],
})

export default router
