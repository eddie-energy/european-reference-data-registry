import createClient, { type Client } from 'openapi-fetch'
import type { components, paths } from './schema'

export const BASE_URL = THYMELEAF_PUBLIC_URL ?? import.meta.env.VITE_BASE_URL

async function fetch(
  init?: RequestInit,
  skipContentType?: boolean,
): Promise<Client<paths, `${string}/${string}`>> {
  //await keycloak.value?.updateToken(5)
  //const token = keycloak.value?.token
  return createClient<paths>({
    baseUrl: `${BASE_URL}/api`,
    headers: {
      ...(!skipContentType && { 'Content-Type': 'application/json' }),
      //Authorization: `Bearer ${token}`,
    },
    credentials: 'include',
    ...init,
  })
}

export async function getAllReferenceDataObjects(): Promise<{
  data?: components['schemas']['ReferenceDataObjectDetail'][]
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).GET('/reference-data-objects')
}

export async function createReferenceDataObject(
  body: components['schemas']['CreateReferenceDataObjectRequest'],
): Promise<{
  data?: components['schemas']['ReferenceDataObjectDetail']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).POST('/reference-data-objects', { body })
}

export async function getReferenceDataObject(
  id: components['parameters']['ReferenceDataObjectId'],
): Promise<{
  data?: components['schemas']['ReferenceDataObjectDetail']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).GET('/reference-data-objects/{id}', { params: { path: { id } } })
}

export async function deleteReferenceDataObject(
  id: components['parameters']['ReferenceDataObjectId'],
): Promise<{
  data?: never
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).DELETE('/reference-data-objects/{id}', { params: { path: { id } } })
}

export async function createVersion(
  id: components['parameters']['ReferenceDataObjectId'],
): Promise<{
  data?: components['schemas']['ReferenceDataObjectVersionDetail']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).POST('/reference-data-objects/{id}/versions', { params: { path: { id } } })
}

export async function publishVersion(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
): Promise<{
  data?: components['schemas']['ReferenceDataObjectVersionDetail']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).POST('/reference-data-objects/{id}/versions/{versionId}/publish', {
    params: { path: { id, versionId } },
  })
}

export async function replaceVersionFields(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  body: components['schemas']['ReplaceVersionFieldsRequest'],
): Promise<{
  data?: components['schemas']['ReferenceDataObjectVersionDetail']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).PUT('/reference-data-objects/{id}/versions/{versionId}/fields', {
    params: { path: { id, versionId } },
    body,
  })
}

export async function createField(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  body: components['schemas']['CreateFieldRequest'],
): Promise<{
  data?: components['schemas']['FieldDto']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).POST('/reference-data-objects/{id}/versions/{versionId}/fields', {
    params: { path: { id, versionId } },
    body,
  })
}

export async function unlinkField(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  fieldId: string,
): Promise<{
  data?: never
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).DELETE(
    '/reference-data-objects/{id}/versions/{versionId}/fields/{fieldId}',
    {
      params: { path: { id, versionId, fieldId } },
    },
  )
}

export async function listEntries(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
): Promise<{
  data?: components['schemas']['EntryDto'][]
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).GET('/reference-data-objects/{id}/versions/{versionId}/entries', {
    params: { path: { id, versionId } },
  })
}

export async function createEntry(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  body: components['schemas']['UpsertEntryRequest'],
): Promise<{
  data?: components['schemas']['EntryDto']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).POST('/reference-data-objects/{id}/versions/{versionId}/entries', {
    params: { path: { id, versionId } },
    body,
  })
}

export async function updateEntry(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  entryId: components['parameters']['EntryId'],
  body: components['schemas']['UpsertEntryRequest'],
): Promise<{
  data?: components['schemas']['EntryDto']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).PUT(
    '/reference-data-objects/{id}/versions/{versionId}/entries/{entryId}',
    {
      params: { path: { id, versionId, entryId } },
      body,
    },
  )
}

export async function deleteEntry(
  id: components['parameters']['ReferenceDataObjectId'],
  entryId: components['parameters']['EntryId'],
): Promise<{
  data?: never
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).DELETE('/reference-data-objects/{id}/entries/{entryId}', {
    params: { path: { id, entryId } },
  })
}
