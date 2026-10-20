import assert from 'node:assert/strict'
import { after, before, beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import svgLoader from 'vite-svg-loader'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter } from 'vue-router'

let server
let user
let objects
let auth
const originalFetch = globalThis.fetch

before(async () => {
  Object.assign(globalThis, {
    THYMELEAF_PUBLIC_URL: 'http://localhost:8080',
    THYMELEAF_KEYCLOAK_URL: 'http://localhost:8081',
    THYMELEAF_KEYCLOAK_REALM: 'ceeds',
    THYMELEAF_KEYCLOAK_CLIENT: 'ceeds-frontend',
  })
  server = await createServer({
    configFile: false,
    plugins: [vue(), svgLoader()],
    resolve: { alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) } },
    server: { middlewareMode: true, watch: null, ws: false },
  })
  user = await server.ssrLoadModule('/src/stores/userInfo.ts')
  objects = await server.ssrLoadModule('/src/stores/referenceDataObject.ts')
  auth = await server.ssrLoadModule('/src/keycloak.ts')
})

after(async () => {
  await server?.close()
})

beforeEach(() => {
  globalThis.fetch = originalFetch
  auth.keycloak.value = undefined
  user.userRole.value = 'operationalEntity'
  user.coordinatingNdsfNations.value = []
  objects.referenceDataObjects.value = [{ id: 'object', name: 'Permissions', versions: [] }]
})

const render = async (path, props = {}) => {
  const { default: component } = await server.ssrLoadModule(`/src/${path}`)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }],
  })
  await router.push('/')
  await router.isReady()
  return renderToString(createSSRApp(component, props).use(router))
}

const organization = {
  id: 'org',
  alias: 'eda',
  name: 'EDA',
  role: 'NDSF',
  editable: true,
  nations: ['GER'],
  responsibilities: [],
}

test('Operational Entity can create and promote coordinating NDSF organizations', async () => {
  assert.match(await render('components/OrganizationCreateForm.vue'), /value="COORDINATING_NDSF"/)
  assert.match(
    await render('components/OrganizationEditForm.vue', { organization }),
    /value="COORDINATING_NDSF"/,
  )
})

test('coordinator sees responsibilities navigation without user administration', async () => {
  user.userRole.value = 'coordinatingNdsf'
  const html = await render('components/SidebarNavigation.vue')
  assert.match(html, /href="\/management\/responsibilities"/)
  assert.doesNotMatch(html, /href="\/management\/users"/)
})

test('coordinator assignment form only offers coordinating nations and hides settings', async () => {
  user.userRole.value = 'coordinatingNdsf'
  user.coordinatingNdsfNations.value = ['FRA']
  const html = await render('components/OrganizationEditForm.vue', {
    organization: { ...organization, editable: false },
  })
  assert.match(html, /value="FRA"/)
  assert.doesNotMatch(html, /value="AUT"|value="GER"|id="organization-form"/)
})

test('coordinator without nations cannot assign responsibilities', async () => {
  user.userRole.value = 'coordinatingNdsf'
  const html = await render('components/OrganizationEditForm.vue', {
    organization: { ...organization, editable: false },
  })
  assert.doesNotMatch(html, /class="add"/)
})

test('ordinary NDSF has no responsibilities navigation', async () => {
  user.userRole.value = 'ndsf'
  assert.doesNotMatch(
    await render('components/SidebarNavigation.vue'),
    /href="\/management\/responsibilities"/,
  )
})

test('Operational Entity retains all nation choices', async () => {
  const html = await render('components/OrganizationEditForm.vue', { organization })
  for (const nation of ['AUT', 'FRA', 'ESP', 'GER']) {
    assert.match(html, new RegExp(`value="${nation}"`))
  }
  assert.match(html, /id="organization-form"/)
})

test('user info keeps coordinating scope separate from maintenance assignments', async () => {
  auth.keycloak.value = { authenticated: true, updateToken: async () => false }
  globalThis.fetch = async (request) => {
    const data = request.url.endsWith('/me')
      ? {
          username: 'coordinator',
          roles: ['PARTICIPANT', 'NDSF', 'COORDINATING_NDSF'],
          ndsfNations: ['AUT', 'GER'],
          coordinatingNdsfNations: ['AUT'],
          organizations: ['eda', 'ordinary'],
        }
      : [{ referenceDataObjectId: 'object', nations: ['GER'] }]
    return Response.json(data)
  }

  assert.deepEqual(await user.updateUserInfo(), {})
  assert.equal(user.userRole.value, 'coordinatingNdsf')
  assert.deepEqual([...user.coordinatingNdsfNations.value], ['AUT'])
  assert.deepEqual([...user.maintainableNations('object')], ['GER'])
  assert.deepEqual([...user.maintainableNations('unassigned')], [])

  auth.keycloak.value = undefined
  await user.updateUserInfo()
  assert.equal(user.userRole.value, 'viewer')
  assert.deepEqual([...user.coordinatingNdsfNations.value], [])
  assert.deepEqual([...user.maintainableNations('object')], [])
})

test('only Operational Entity can start new versions in the object editor', async () => {
  objects.referenceDataObject.value = {
    id: 'object',
    name: 'Permissions',
    description: '',
    versions: [{ id: 'version', versionCode: 1, publishState: 'PUBLISHED', fields: [] }],
  }
  for (const role of ['coordinatingNdsf', 'ndsf']) {
    user.userRole.value = role
    assert.doesNotMatch(
      await render('components/ReferenceDataObjectEditor.vue', { id: 'object' }),
      /Start new version/,
    )
  }
  user.userRole.value = 'operationalEntity'
  assert.match(
    await render('components/ReferenceDataObjectEditor.vue', { id: 'object' }),
    /Start new version/,
  )
})
