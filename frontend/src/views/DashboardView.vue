<script lang="ts" setup>
import ReferenceObjectCard from '@/components/ReferenceObjectCard.vue'
import 'vue3-carousel/carousel.css'
import { referenceDataObjects } from '@/stores/referenceDataObject'
import { Carousel, Slide, Pagination, Navigation } from 'vue3-carousel'

const carouselConfig = {
  itemsToShow: 3,
  gap: 16,
  wrapAround: true,
}
</script>

<template>
  <main>
    <h1>S3 - CEEDS Reference Data Registry</h1>
    <div>
      <h2>Reference Data Entries</h2>
      <Carousel v-bind="carouselConfig">
        <Slide v-for="object in referenceDataObjects" :key="object.id">
          <ReferenceObjectCard v-bind="object" />
        </Slide>
        <template #addons>
          <Navigation />
          <Pagination />
        </template>
      </Carousel>
    </div>
    <div>
      <h2>Application Programmable Interface (API)</h2>
      <div></div>
    </div>
  </main>
</template>
