<script lang="ts" setup>
import ChevronDownIcon from '@/assets/icons/ChevronDownIcon.svg'
import { computed, nextTick, ref, useId, useTemplateRef } from 'vue'

const { options, placeholder, compact, multiple } = defineProps<{
  options: { label?: string; value: string }[] | string[]
  placeholder: string
  compact?: boolean
  multiple?: boolean
}>()
const model = defineModel<string | string[]>()
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
    .replaceAll(/\p{Diacritic}/gu, '')
    .toLowerCase()

const selectedValues = computed<string[]>(() => {
  if (Array.isArray(model.value)) return model.value
  return model.value === undefined ? [] : [model.value]
})

const isSelected = (value: string) => selectedValues.value.includes(value)

const selectedOptions = computed(() =>
  labelValueOptions.value.filter((option) => isSelected(option.value)),
)

const selectedLabel = computed(() => selectedOptions.value[0]?.label)

const inputValue = computed(() => {
  if (multiple || show.value) return query.value
  return selectedLabel.value ?? ''
})

const inputPlaceholder = computed(() => {
  if (multiple) return selectedOptions.value.length ? '' : placeholder
  return selectedLabel.value ?? placeholder
})

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
  const currentIndex = filteredOptions.value.findIndex((option) => isSelected(option.value))
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

const removeValue = (value: string) => {
  model.value = selectedValues.value.filter((selected) => selected !== value)
}

const handleOptionClick = (value: string) => {
  if (multiple) {
    if (isSelected(value)) removeValue(value)
    else model.value = [...selectedValues.value, value]
    query.value = ''
    activeIndex.value = Math.max(
      filteredOptions.value.findIndex((option) => option.value === value),
      0,
    )
    return
  }
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
    case 'Backspace':
      if (multiple && !query.value && selectedValues.value.length) {
        removeValue(selectedValues.value[selectedValues.value.length - 1]!)
      }
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
    <div
      :class="{ placeholder: !selectedOptions.length && !query, multiple: multiple }"
      class="main-option"
      @click.prevent="toggleOpen"
    >
      <span v-for="option in multiple ? selectedOptions : []" :key="option.value" class="chip">
        {{ option.label ?? option.value }}
        <button
          type="button"
          class="chip-remove"
          :aria-label="`Remove ${option.label ?? option.value}`"
          @mousedown.prevent
          @click.stop="removeValue(option.value)"
        >
          ×
        </button>
      </span>
      <input
        ref="search"
        :aria-activedescendant="show && filteredOptions.length ? activeOptionId : undefined"
        :aria-controls="listboxId"
        :aria-expanded="show"
        :placeholder="inputPlaceholder"
        :value="inputValue"
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
      <div
        v-if="show"
        :id="listboxId"
        :aria-multiselectable="multiple"
        class="options"
        role="listbox"
        @mousedown.prevent
        @click.prevent
      >
        <div
          v-for="(option, index) in filteredOptions"
          :id="optionId(index)"
          :key="option.value"
          :aria-selected="isSelected(option.value)"
          :class="{ selected: isSelected(option.value), active: index === activeIndex }"
          class="option"
          role="option"
          @mousedown.prevent
          @mouseenter="activeIndex = index"
          @click.stop.prevent="handleOptionClick(option.value)"
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

.main-option.multiple {
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: var(--spacing-xs);

  .search {
    flex: 1 1 6rem;
  }

  .icon {
    margin-left: auto;
  }
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-sm);
  border-radius: var(--chip-radius);
  background-color: var(--teal-tint-bg);
  color: var(--teal-tint-text);
  font-size: 0.875rem;
}

.chip-remove {
  display: flex;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  line-height: 1;
  cursor: pointer;
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
