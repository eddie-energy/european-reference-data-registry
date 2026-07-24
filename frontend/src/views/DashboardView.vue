<script lang="ts" setup>
import ReferenceObjectCard from '@/components/ReferenceObjectCard.vue'
import 'vue3-carousel/carousel.css'
import { referenceDataObjects } from '@/stores/referenceDataObject'
import { Carousel, Slide, Pagination, Navigation } from 'vue3-carousel'
import ButtonLink from '@/components/ButtonLink.vue'
import { userRole } from '@/stores/userInfo'
import { computed } from 'vue'

const itemsToShow = 3

const visibleReferenceDataObjects = computed(() =>
  userRole.value === 'ceedsEntity'
    ? referenceDataObjects.value
    : referenceDataObjects.value?.filter((object) =>
        object.versions.some((version) => version.publishState === 'PUBLISHED'),
      ),
)

const carouselConfig = computed(() => ({
  itemsToShow,
  gap: 64,
  wrapAround: (visibleReferenceDataObjects.value?.length ?? 0) > itemsToShow,
}))
</script>

<template>
  <main class="dashboard">
    <h1>S3 - CEEDS Reference Data Registry</h1>
    <section>
      <header class="header">
        <div class="section-heading">
          <span class="badge badge-teal">Reference Data</span>
          <h2 class="carousel-title">Reference Data Entries</h2>
        </div>
        <ButtonLink
          v-if="userRole === 'ceedsEntity'"
          component="RouterLink"
          to="/reference-data-objects/create"
        >
          Create new
        </ButtonLink>
      </header>
      <div class="carousel-wrapper">
        <Carousel v-bind="carouselConfig">
          <Slide v-for="object in visibleReferenceDataObjects" :key="object.id">
            <ReferenceObjectCard v-bind="object" />
          </Slide>
          <template #addons>
            <Navigation />
            <Pagination />
          </template>
        </Carousel>
      </div>
    </section>
    <section>
      <div class="section-heading">
        <span class="badge badge-teal">Developers</span>
        <h2 class="carousel-title">Application Programmable Interface (API)</h2>
      </div>
      <div></div>
    </section>
  </main>
</template>

<style scoped>
.dashboard {
  padding-block: var(--spacing-xlg);
}
.header {
  display: flex;
  gap: 2rem;
  align-items: center;
}

h1 {
  margin: 0 0 var(--spacing-xxl);
  font-size: 1.75rem;
  text-align: center;
}

section {
  margin-bottom: var(--spacing-xxl);
}

section h2 {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 700;
}

.section-heading {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
  padding-inline-start: calc(var(--spacing-xxl) + var(--spacing-sm));
}

.header .section-heading {
  margin-bottom: 0;
}

.carousel-wrapper {
  width: 100%;
  max-width: 100%;
  padding-inline: var(--spacing-xxl);
  overflow: hidden;
  box-sizing: border-box;
}

:deep(.carousel) {
  width: 100%;
  max-width: 100%;
}

:deep(.carousel__slide) {
  padding: var(--spacing-sm);
  align-items: stretch;
}

:deep(.carousel__prev) {
  inset-inline-start: calc(var(--spacing-sm) - var(--spacing-xxl));
}

:deep(.carousel__next) {
  inset-inline-end: calc(var(--spacing-sm) - var(--spacing-xxl));
}

:deep(.carousel__pagination) {
  position: static;
  left: auto;
  transform: none;
  margin-top: var(--spacing-xlg);
}
</style>
