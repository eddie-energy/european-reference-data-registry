import Keycloak from 'keycloak-js'
import { ref } from 'vue'
import { BASE_URL } from './config'

export const keycloak = ref<Keycloak>()

const keycloakConfig = {
  url: THYMELEAF_KEYCLOAK_URL ?? import.meta.env.VITE_KEYCLOAK_URL,
  realm: THYMELEAF_KEYCLOAK_REALM ?? import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: THYMELEAF_KEYCLOAK_CLIENT ?? import.meta.env.VITE_KEYCLOAK_CLIENT,
}

export const login = async () => {
  keycloak.value = new Keycloak(keycloakConfig)
  await keycloak.value.init({
    onLoad: 'login-required',
    checkLoginIframe: false,
    redirectUri: import.meta.env.DEV ? globalThis.location.origin : BASE_URL,
  })
  // checkLoginIframe maybe needs to be set to true in prod.
  localStorage.setItem('access-token', keycloak.value.token ?? '')
  startTokenRefresh()
}

export const logout = () => {
  keycloak.value!.logout()
  localStorage.removeItem('access-token')
}

const startTokenRefresh = () => {
  setInterval(() => {
    keycloak.value
      ?.updateToken(70)
      .then((refreshed) => {
        if (refreshed) {
          localStorage.setItem('access-token', keycloak.value!.token ?? '')
        } else {
          console.log(
            'Token not refreshed, valid for ' +
              Math.round(
                (keycloak.value?.tokenParsed?.exp ?? 0) +
                  (keycloak.value?.timeSkew ?? 0) -
                  Date.now() / 1000,
              ) +
              ' seconds',
          )
        }
        return refreshed
      })
      .catch(() => {
        console.log('Failed to refresh token')
      })
  }, 60000)
}
