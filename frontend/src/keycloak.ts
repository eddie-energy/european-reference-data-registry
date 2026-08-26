import Keycloak from 'keycloak-js'
import { ref } from 'vue'
import { BASE_URL } from './config'

export const keycloak = ref<Keycloak>()

const keycloakConfig = {
  url: THYMELEAF_KEYCLOAK_URL ?? import.meta.env.VITE_KEYCLOAK_URL,
  realm: THYMELEAF_KEYCLOAK_REALM ?? import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: THYMELEAF_KEYCLOAK_CLIENT ?? import.meta.env.VITE_KEYCLOAK_CLIENT,
}

const redirectUri = () => (import.meta.env.DEV ? globalThis.location.origin : BASE_URL)

export const initAuth = async () => {
  keycloak.value = new Keycloak(keycloakConfig)
  await keycloak.value.init({
    onLoad: 'check-sso',
    checkLoginIframe: false,
    redirectUri: redirectUri(),
  })
  if (keycloak.value.authenticated) {
    localStorage.setItem('access-token', keycloak.value.token ?? '')
    startTokenRefresh()
  } else {
    localStorage.removeItem('access-token')
  }
}

export const isAuthenticated = () => keycloak.value?.authenticated === true

export const login = () => {
  keycloak.value?.login({ redirectUri: redirectUri() })
}

export const register = () => {
  keycloak.value?.register({ redirectUri: redirectUri() })
}

export const logout = () => {
  localStorage.removeItem('access-token')
  keycloak.value?.logout({ redirectUri: redirectUri() })
}

const startTokenRefresh = () => {
  setInterval(() => {
    keycloak.value
      ?.updateToken(70)
      .then((refreshed) => {
        if (refreshed) {
          localStorage.setItem('access-token', keycloak.value!.token ?? '')
        }
        return refreshed
      })
      .catch(() => {
        console.log('Failed to refresh token')
      })
  }, 60000)
}
