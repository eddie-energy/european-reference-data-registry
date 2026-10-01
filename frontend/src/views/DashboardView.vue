<script lang="ts" setup>
import ReferenceDataObjectCard from '@/components/ReferenceDataObjectCard.vue'
import 'vue3-carousel/carousel.css'
import { referenceDataObjects, updateReferenceDataObjects } from '@/stores/referenceDataObject'
import { Carousel, Slide, Pagination, Navigation } from 'vue3-carousel'
import ButtonLink from '@/components/ButtonLink.vue'
import { userRole } from '@/stores/userInfo'
import useToast from '@/composables/useToast'
import { computed, onMounted, ref } from 'vue'
import type { components } from '@/schema'

onMounted(async () => {
  const { error } = await updateReferenceDataObjects()
  if (error) {
    useToast().danger(error.message ?? 'Failed to load reference data objects')
  }
})

const visibleReferenceDataObjects = computed(() =>
  userRole.value === 'operationalEntity'
    ? referenceDataObjects.value
    : referenceDataObjects.value?.filter((referenceDataObject) =>
        referenceDataObject.versions.some((version) => version.publishState === 'PUBLISHED'),
      ),
)

type Category = components['schemas']['ReferenceDataObjectCategory']
const categoryFilter = ref<Category>()
const categoryFilters: { value: Category | undefined; label: string }[] = [
  { value: undefined, label: 'All' },
  { value: 'ROLE', label: 'Role' },
  { value: 'SERVICE', label: 'Service' },
]

const filteredReferenceDataObjects = computed(() =>
  (visibleReferenceDataObjects.value ?? []).filter(
    (object) => !categoryFilter.value || object.category === categoryFilter.value,
  ),
)

const carouselKey = computed(() =>
  filteredReferenceDataObjects.value
    .map(
      (object) =>
        `${object.id}:${object.versions.map((version) => `${version.versionCode}${version.publishState}`).join(',')}`,
    )
    .join('|'),
)

const carouselConfig = computed(() => ({
  itemsToShow: 1,
  gap: 64,
  wrapAround: filteredReferenceDataObjects.value.length > 3,
  breakpoints: {
    640: { itemsToShow: 2 },
    1024: { itemsToShow: 3 },
  },
}))
</script>

<template>
  <main class="dashboard">
    <h1>S3 - CEEDS Reference Data Registry</h1>
    <section>
      <header class="header">
        <div class="section-heading">
          <span class="badge badge-teal">Reference Data</span>
          <h2 class="carousel-title">Reference Data Objects</h2>
        </div>
        <ButtonLink
          v-if="userRole === 'operationalEntity'"
          component="RouterLink"
          to="/reference-data-objects/create"
        >
          Create new
        </ButtonLink>
      </header>
      <div class="category-filters" role="group" aria-label="Filter by category">
        <button
          v-for="filter in categoryFilters"
          :key="filter.label"
          type="button"
          class="filter-chip"
          :class="{ selected: categoryFilter === filter.value }"
          :aria-pressed="categoryFilter === filter.value"
          @click="categoryFilter = filter.value"
        >
          {{ filter.label }}
        </button>
      </div>
      <div class="carousel-wrapper">
        <Carousel v-if="filteredReferenceDataObjects.length" :key="carouselKey" v-bind="carouselConfig">
          <Slide
            v-for="referenceDataObject in filteredReferenceDataObjects"
            :key="referenceDataObject.id"
          >
            <ReferenceDataObjectCard v-bind="referenceDataObject" />
          </Slide>
          <template #addons>
            <Navigation />
            <Pagination />
          </template>
        </Carousel>
        <p v-else>No reference data objects in this category.</p>
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

.category-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg) var(--spacing-xxl);
}

.filter-chip {
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--input-border-color);
  border-radius: var(--chip-radius);
  background: var(--light);
  color: var(--dark);
  cursor: pointer;
}

.filter-chip.selected {
  border-color: var(--teal);
  background: var(--teal-tint-bg);
  color: var(--teal-tint-text);
}

.filter-chip:focus-visible {
  outline: 2px solid var(--teal);
  outline-offset: 2px;
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
