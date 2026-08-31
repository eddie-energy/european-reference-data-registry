import createClient, { type Client } from 'openapi-fetch'
import type { components, paths } from './schema'
import { BASE_URL } from './config'
import { keycloak } from './keycloak'

export { BASE_URL }

async function fetch(
  init?: RequestInit,
  skipContentType?: boolean,
): Promise<Client<paths, `${string}/${string}`>> {
  if (keycloak.value?.authenticated) {
    await keycloak.value.updateToken(5)
  }
  const token = keycloak.value?.token
  return createClient<paths>({
    baseUrl: `${BASE_URL}/api`,
    headers: {
      ...(!skipContentType && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: `Bearer ${token}` }),
    },
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

export async function deleteVersion(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
): Promise<{
  data?: never
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).DELETE('/reference-data-objects/{id}/versions/{versionId}', {
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

export async function reorderFields(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  fieldIds: string[],
): Promise<{
  data?: components['schemas']['ReferenceDataObjectVersionDetail']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).PUT('/reference-data-objects/{id}/versions/{versionId}/fields/order', {
    params: { path: { id, versionId } },
    body: { fieldIds },
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

export async function listReferenceDataEntries(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
): Promise<{
  data?: components['schemas']['ReferenceDataEntryDto'][]
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).GET(
    '/reference-data-objects/{id}/versions/{versionId}/reference-data-entries',
    {
      params: { path: { id, versionId } },
    },
  )
}

export async function createReferenceDataEntry(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  body: components['schemas']['UpsertReferenceDataEntryRequest'],
): Promise<{
  data?: components['schemas']['ReferenceDataEntryDto']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).POST(
    '/reference-data-objects/{id}/versions/{versionId}/reference-data-entries',
    {
      params: { path: { id, versionId } },
      body,
    },
  )
}

export async function updateReferenceDataEntry(
  id: components['parameters']['ReferenceDataObjectId'],
  versionId: components['parameters']['VersionId'],
  referenceDataEntryId: components['parameters']['ReferenceDataEntryId'],
  body: components['schemas']['UpsertReferenceDataEntryRequest'],
): Promise<{
  data?: components['schemas']['ReferenceDataEntryDto']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).PUT(
    '/reference-data-objects/{id}/versions/{versionId}/reference-data-entries/{referenceDataEntryId}',
    {
      params: { path: { id, versionId, referenceDataEntryId } },
      body,
    },
  )
}

export async function deleteReferenceDataEntry(
  id: components['parameters']['ReferenceDataObjectId'],
  referenceDataEntryId: components['parameters']['ReferenceDataEntryId'],
): Promise<{
  data?: never
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).DELETE(
    '/reference-data-objects/{id}/reference-data-entries/{referenceDataEntryId}',
    {
      params: { path: { id, referenceDataEntryId } },
    },
  )
}

export async function getCurrentUser(): Promise<{
  data?: components['schemas']['CurrentUserDto']
  error?: components['schemas']['ErrorResponse']
  response: Response
}> {
  return (await fetch()).GET('/me')
}
