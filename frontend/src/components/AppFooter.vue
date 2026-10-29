<script setup lang="ts">
const links = [
  { label: 'Imprint', href: 'https://insieme.energy/html/imprint.html' },
  { label: 'Privacy Policy', href: 'https://insieme.energy/html/privacy.html' },
]
</script>

<template>
  <footer class="footer">
    <a
      v-for="link in links"
      :key="link.label"
      :href="link.href"
      target="_blank"
      rel="noopener noreferrer"
      >{{ link.label }}</a
    >
  </footer>
</template>

<style scoped>
.footer {
  display: flex;
  justify-content: center;
  gap: var(--spacing-xlg);
  padding: var(--spacing-md);
  border-top: 1px solid var(--border-color);
  background: var(--surface-alt);
}

a {
  color: var(--teal-tint-text);
  text-decoration: none;
}

a:hover,
a:focus-visible {
  text-decoration: underline;
}
</style>
