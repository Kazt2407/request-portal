{{/* Name of a service's Deployment, Service and pods, e.g. request-portal-org-service. */}}
{{- define "request-portal.serviceName" -}}
{{- printf "%s-%s" .root.Release.Name .name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "request-portal.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: request-portal
app.kubernetes.io/version: {{ .root.Values.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
{{- end -}}

{{- define "request-portal.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end -}}

{{- define "request-portal.secretName" -}}
{{- default (printf "%s-credentials" .Release.Name) .Values.secrets.existingSecret -}}
{{- end -}}

{{/* In-cluster URL of another service, e.g. http://request-portal-org-service:8081. */}}
{{- define "request-portal.url" -}}
{{- $service := index .root.Values.services .name -}}
{{- printf "http://%s:%v" (include "request-portal.serviceName" .) $service.port -}}
{{- end -}}
