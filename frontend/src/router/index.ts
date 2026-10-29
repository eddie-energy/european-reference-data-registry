import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import ReferenceDataObjectView from '@/views/ReferenceDataObjectView.vue'
import CreateReferenceDataObjectView from '@/views/CreateReferenceDataObjectView.vue'
import UserManagementView from '@/views/UserManagementView.vue'
import ResponsibilityManagementView from '@/views/ResponsibilityManagementView.vue'
import { mayManageResponsibilities, userRole } from '@/stores/userInfo'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      meta: { title: 'Dashboard' },
      component: DashboardView,
    },
    {
      path: '/management/responsibilities',
      name: 'responsibility-management',
      meta: { title: 'Responsibilities' },
      component: ResponsibilityManagementView,
      beforeEnter: () => {
        if (!mayManageResponsibilities.value) return { name: 'dashboard' }
      },
    },
    {
      path: '/management/users',
      name: 'user-management',
      meta: { title: 'Users' },
      component: UserManagementView,
      beforeEnter: () => {
        if (userRole.value !== 'operationalEntity') {
          return { name: 'dashboard' }
        }
      },
    },
    {
      path: '/reference-data-objects/create',
      name: 'reference-data-object-create',
      meta: { title: 'Create Reference Data Object' },
      component: CreateReferenceDataObjectView,
      beforeEnter: () => {
        if (userRole.value !== 'operationalEntity') {
          return { name: 'dashboard' }
        }
      },
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
