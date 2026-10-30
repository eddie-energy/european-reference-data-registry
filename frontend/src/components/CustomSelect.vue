<script lang="ts" setup>
import ChevronDownIcon from '@/assets/icons/ChevronDownIcon.svg'
import { computed, nextTick, ref, useId, useTemplateRef } from 'vue'

const { options, placeholder, compact } = defineProps<{
  options: { label?: string; value: string }[] | string[]
  placeholder: string
  compact?: boolean
}>()
const model = defineModel<string>()
const show = ref(false)
const openUpward = ref(false)
const activeIndex = ref(0)
const parentDiv = useTemplateRef('parent')
const listboxId = useId()

const optionId = (index: number) => `${listboxId}-option-${index}`
const activeOptionId = computed(() => optionId(activeIndex.value))

const labelValueOptions = computed<{ label?: string; value: string }[]>(() => {
  return options.map((option) =>
    typeof option === 'string' ? { label: option, value: option } : option,
  )
})

function getDropdownMaxHeightPx(el: HTMLElement): number {
  const rootFontSize = Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
  const raw = getComputedStyle(el).getPropertyValue('--dropdown-max-height').trim()
  const value = Number.parseFloat(raw)
  if (Number.isNaN(value)) return 15 * rootFontSize
  return raw.endsWith('rem') ? value * rootFontSize : value
}

function shouldOpenUpward(el: HTMLElement): boolean {
  const rect = el.getBoundingClientRect()
  const maxHeightPx = getDropdownMaxHeightPx(el)
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top
  return spaceBelow < maxHeightPx && spaceAbove > spaceBelow
}

const query = ref('')
const searchInput = useTemplateRef('search')

const normalize = (text: string) =>
  text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()

const selectedLabel = computed(
  () => labelValueOptions.value.find((option) => option.value === model.value)?.label,
)

const filteredOptions = computed(() => {
  const needle = normalize(query.value.trim())
  if (!needle) return labelValueOptions.value
  return labelValueOptions.value.filter((option) =>
    normalize(option.label ?? option.value).includes(needle),
  )
})

async function scrollActiveIntoView() {
  await nextTick()
  document.getElementById(optionId(activeIndex.value))?.scrollIntoView({ block: 'nearest' })
}

function openDropdown() {
  if (parentDiv.value) openUpward.value = shouldOpenUpward(parentDiv.value)
  const currentIndex = filteredOptions.value.findIndex((option) => option.value === model.value)
  activeIndex.value = Math.max(currentIndex, 0)
  show.value = true
  scrollActiveIntoView()
}

function closeDropdown() {
  show.value = false
  query.value = ''
}

function toggleOpen() {
  searchInput.value?.focus()
  if (show.value) closeDropdown()
  else openDropdown()
}

function handleInput(e: Event) {
  query.value = (e.target as HTMLInputElement).value
  if (!show.value) openDropdown()
  activeIndex.value = 0
  scrollActiveIntoView()
}

function moveActive(delta: number) {
  if (!show.value) {
    openDropdown()
    return
  }
  const len = filteredOptions.value.length
  if (!len) return
  activeIndex.value = (activeIndex.value + delta + len) % len
  scrollActiveIntoView()
}

const handleOptionClick = (value: string) => {
  closeDropdown()
  model.value = value
}

function selectActive() {
  const option = filteredOptions.value[activeIndex.value]
  if (option) handleOptionClick(option.value)
}

function handleKeydown(e: KeyboardEvent) {
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      moveActive(1)
      break
    case 'ArrowUp':
      e.preventDefault()
      moveActive(-1)
      break
    case 'Enter':
      e.preventDefault()
      if (show.value) selectActive()
      else openDropdown()
      break
    case 'Escape':
      closeDropdown()
      break
  }
}

const handleBlur = () => closeDropdown()
</script>

<template>
  <div
    ref="parent"
    :class="{ 'is-open': show, 'flip-up': openUpward, compact: compact }"
    class="select"
  >
    <div :class="{ placeholder: !selectedLabel && !query }" class="main-option" @click="toggleOpen">
      <input
        ref="search"
        :aria-activedescendant="show && filteredOptions.length ? activeOptionId : undefined"
        :aria-controls="listboxId"
        :aria-expanded="show"
        :placeholder="selectedLabel ?? placeholder"
        :value="show ? query : (selectedLabel ?? '')"
        aria-autocomplete="list"
        aria-haspopup="listbox"
        autocomplete="off"
        class="search"
        role="combobox"
        type="text"
        @blur="handleBlur"
        @input="handleInput"
        @keydown="handleKeydown"
      />
      <ChevronDownIcon class="icon" />
    </div>
    <Transition>
      <div v-if="show" :id="listboxId" class="options" role="listbox">
        <div
          v-for="(option, index) in filteredOptions"
          :id="optionId(index)"
          :key="option.value"
          :aria-selected="model === option.value"
          :class="{ selected: model === option.value, active: index === activeIndex }"
          class="option"
          role="option"
          @mousedown.prevent
          @mouseenter="activeIndex = index"
          @click.stop="handleOptionClick(option.value)"
        >
          {{ option.label }}
        </div>
        <div v-if="!filteredOptions.length" class="no-results">No results</div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.select {
  position: relative;
  --dropdown-max-height: 15rem;
  color: var(--dark);
  cursor: pointer;
  font-size: 1rem;
  line-height: 1.5;
  transition:
    border-radius 0.3s ease-in-out,
    border-color 0.3s ease-in-out;
  height: fit-content;
  box-sizing: border-box;

  &.is-open {
    .icon {
      transform: rotate(180deg);
    }

    .main-option {
      border-color: var(--primary);
      border-bottom-color: transparent;
      border-radius: var(--spacing-sm) var(--spacing-sm) 0 0;
    }

    &.flip-up .main-option {
      border-bottom-color: var(--primary);
      border-top-color: transparent;
      border-radius: 0 0 var(--spacing-sm) var(--spacing-sm);
    }
  }

  &.compact {
    --dropdown-max-height: 8rem;
  }
}

.icon {
  transition: transform 0.3s ease-in-out;
}

.main-option {
  display: flex;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-xlg);
  align-items: center;
  border: 1px solid var(--input-border-color);
  border-radius: var(--spacing-sm);
  background-color: var(--light);
  transition:
    border-radius 0.3s ease-in-out,
    border-color 0.3s ease-in-out;

  &.placeholder {
    color: var(--dark);
  }
}

.search {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  font: inherit;
  color: inherit;
  cursor: inherit;
  outline: none;

  &::placeholder {
    color: inherit;
    opacity: 1;
  }
}

.main-option.placeholder .search::placeholder {
  color: var(--dark);
  opacity: 0.6;
}

.main-option:focus-within {
  border-color: var(--teal);
  box-shadow: 0 0 0 3px var(--teal-tint-bg);
}

.no-results {
  padding: var(--spacing-sm) var(--spacing-xlg);
  opacity: 0.6;
}

.options {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 1;
  top: 100%;
  width: auto;
  box-sizing: border-box;
  background-color: var(--light);
  outline: 1px solid var(--primary);
  border-radius: 0 0 var(--spacing-sm) var(--spacing-sm);
  max-height: var(--dropdown-max-height);
  overflow-x: hidden;
  overflow-y: auto;
}

.select.flip-up .options {
  top: auto;
  bottom: 100%;
  border-radius: var(--spacing-sm) var(--spacing-sm) 0 0;
}

.option {
  border-left: 3px solid transparent;
  padding: var(--spacing-sm) var(--spacing-xlg);
  transition:
    border-color 0.3s ease-in-out,
    background-color 0.3s ease-in-out;

  &:hover,
  &.active {
    background-color: var(--surface-alt);
    border-color: var(--primary);
  }

  &.selected {
    background-color: var(--teal-tint-bg);
    border-color: var(--primary);
  }
}

.v-enter-active,
.v-leave-active {
  transition: opacity 0.3s ease;
}

.v-enter-from,
.v-leave-to {
  opacity: 0;
}
</style>
