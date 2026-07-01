<script lang="ts" setup>
import { referenceDataObject, updateReferenceDataObject } from '@/stores/referenceDataObject'
import { onMounted, watch } from 'vue'

const { id } = defineProps<{ id: string }>()

const load = () => updateReferenceDataObject(id)

onMounted(load)
watch(() => id, load)
</script>

<template>
  <main>
    <template v-if="referenceDataObject">
      <h1>{{ referenceDataObject.name }}</h1>
      <p>{{ referenceDataObject.description }}</p>

      <section v-for="version in referenceDataObject.versions" :key="version.id">
        <h2>Version {{ version.versionCode }} ({{ version.publishState }})</h2>
        <ul>
          <li v-for="field in version.fields" :key="field.id">
            {{ field.name }} — {{ field.dataType }}
            <template v-if="field.mandatory">(mandatory)</template>
            <template v-if="field.nation">— {{ field.nation }}</template>
          </li>
        </ul>
      </section>
    </template>
  </main>
</template>
