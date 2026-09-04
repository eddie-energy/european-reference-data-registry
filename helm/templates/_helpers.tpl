{{/*
Selector labels postgres
*/}}
{{- define "european-reference-data-registry.selectorLabels.postgres" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: postgres
{{- end }}

{{/*
Selector labels keycloak
*/}}
{{- define "european-reference-data-registry.selectorLabels.keycloak" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: keycloak
{{- end }}
{{/*

Selector labels core
*/}}
{{- define "european-reference-data-registry.selectorLabels.core" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: core
{{- end }}

{{/*
Render a container resources section when resources are configured.
*/}}
{{- define "european-reference-data-registry.resources" -}}
{{- with . }}
resources:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
Construct the external Keycloak URL.
*/}}
{{- define "european-reference-data-registry.keycloak.url" -}}
{{- if .Values.ingress.tls.enabled }}https://{{ else }}http://{{ end }}{{ .Values.ingress.host }}{{ .Values.ingress.path }}{{ .Values.keycloak.httpRelativePath }}
{{- end }}
