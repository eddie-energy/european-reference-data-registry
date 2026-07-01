<script lang="ts" setup>
import type { components } from '@/schema'
import ButtonLink from './ButtonLink.vue'
import ArrowRightIcon from '@/assets/icons/ArrowRightIcon.svg'

const { name, description, id } = defineProps<components['schemas']['ReferenceDataObjectDetail']>()
</script>

<template>
  <div>
    <h3>{{ name }}</h3>
    <p>{{ description }}</p>
    <ButtonLink component="RouterLink" :to="`/reference-data-objects/${id}`">
      Explore <ArrowRightIcon />
    </ButtonLink>
  </div>
</template>
