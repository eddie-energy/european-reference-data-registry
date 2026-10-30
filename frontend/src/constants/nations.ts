import { countries } from 'countries-list'
import type { components } from '@/schema'

type Nation = components['schemas']['Nation']

const NATION_ISO2: Record<Nation, keyof typeof countries> = {
  AUT: 'AT',
  BEL: 'BE',
  BGR: 'BG',
  CYP: 'CY',
  CZE: 'CZ',
  DEU: 'DE',
  DNK: 'DK',
  ESP: 'ES',
  EST: 'EE',
  FIN: 'FI',
  FRA: 'FR',
  GRC: 'GR',
  HRV: 'HR',
  HUN: 'HU',
  IRL: 'IE',
  ITA: 'IT',
  LTU: 'LT',
  LUX: 'LU',
  LVA: 'LV',
  MLT: 'MT',
  NLD: 'NL',
  POL: 'PL',
  PRT: 'PT',
  ROU: 'RO',
  SVK: 'SK',
  SVN: 'SI',
  SWE: 'SE',
  USA: 'US',
}

const flagEmoji = (code: string): string =>
  String.fromCodePoint(...[...code.toUpperCase()].map((c) => 0x1f1e6 + c.codePointAt(0)! - 65))

export const nations: { value: Nation; label: string }[] = (Object.keys(NATION_ISO2) as Nation[])
  .map((value) => ({ value, name: countries[NATION_ISO2[value]].name }))
  .sort((a, b) => a.name.localeCompare(b.name))
  .map(({ value, name }) => ({ value, label: `${flagEmoji(NATION_ISO2[value])} ${name}` }))
